#!/usr/bin/env ruby

# Setup for Keywhiz integration tests.

require 'fileutils'
require 'tmpdir'
require 'timeout'
require 'socket'
include Socket::Constants

puts `gem install rest-client`
puts `gem install addressable`

require 'rubygems'
require 'rest_client'
require 'addressable/uri'

def putsf(arg)
  puts arg
  $stdout.flush # (depends on OS, but should work)
end

def checkout_keywhiz_fs(keywhiz_base_dir, mountpoint)
  putsf "[*] Checking out keywhiz-fs"
  Kernel.system "git clone git@github.com:square/keywhiz-fs.git"
  raise RuntimeError unless $? == 0

  begin
    Dir.chdir "keywhiz-fs"
    Kernel.system "make"
    raise RuntimeError unless $? == 0
  ensure
    Dir.chdir ".."
  end

  cacert = File.join(keywhiz_base_dir, "src/test/resources/CA/cacert.crt")
  client_key = File.join("keywhiz-fs/test/client.pem")
  Kernel.system "mkdir #{mountpoint}"
  putsf "keywhiz-fs/keywhiz-fs -f -v -C #{cacert} -k #{client_key} https://localhost:4444 #{mountpoint}"
  process = IO.popen "keywhiz-fs/keywhiz-fs -f -v -C #{cacert} -k #{client_key} https://localhost:4444 #{mountpoint}"
  raise RuntimeError unless $? == 0 && process
  putsf "[*] Keywhiz-fs started with pid #{process.pid}"
  process
end

def reset_db_and_execute_server(original_dir)
  putsf "[*] Reseting Keywhiz Database"
  Kernel.system "dropdb keywhiz_development"
  Kernel.system "createdb keywhiz_development"

  putsf "[*] Starting Keywhiz server"
  jars = Dir.glob(File.join(original_dir, "server/target/keywhiz-server-*-SNAPSHOT-shaded.jar"))
  raise RuntimeError, "Cannot find keywhiz JAR" if jars.empty?
  jar = jars.last

  putsf "java -jar #{jar} migrate server/src/main/resources/keywhiz-development.yaml"
  Kernel.system "java -jar #{jar} migrate server/src/main/resources/keywhiz-development.yaml"

  putsf "java -jar #{jar} server server/src/main/resources/keywhiz-development.yaml 2>&1"
  process = IO.popen "java -jar #{jar} server server/src/main/resources/keywhiz-development.yaml 1>&2"
  putsf "[*] Keywhiz server started with pid #{process.pid}"
  # wait until socket is open
  timed_connect_loop(4444)
  process
end

def timed_connect_loop(port, timeout = 360)
  Timeout::timeout(timeout) {
    return connect_loop port
  }
end

def connect_loop(port)
  putsf "[*] Attempting to connect to localhost on port #{port} (may take some time)."
  while true
    begin
      socket = Socket.new( AF_INET, SOCK_STREAM, 0 )
      sockaddr = Socket.pack_sockaddr_in( port, '127.0.0.1' )
      socket.connect( sockaddr )
      socket.close
      putsf "[**] Connection established!"
      return

    rescue Exception => e
      # Loop until it works
      putsf e
      # If our service isn't up yet, give it some time.
      if e.message.include? "Connection refused"
        sleep 0.25
      end
    end
  end
end

Signal.trap("CHLD") do
  Process.wait rescue ''
end

Signal.trap("INT") {}
Signal.trap("TERM") {}

# When running under Jenkins, our parent is the java-controller; and we run in the same process-group.
# if/when we kill our children (using kill-process-group, see below) we'll end up signalling our parent
# too -- which is obviously not what we want.
mysession = Process.setsid rescue ''

original_dir = Dir.getwd
keywhiz_base_dir = File.join original_dir, "server"

putsf "[*] Maven build of keywhiz: orig #{original_dir} keywhiz_base #{keywhiz_base_dir}"
# Skipping tests for speed. This assumes build tests are already run in another Jenkins job.
Kernel.system "mvn package -Dmaven.test.skip=true -am -pl server"
raise RuntimeError unless $? == 0
putsf "[*] Finished building keywhiz (server and client)"

server_process = kwfs_process = nil
ret = false
workspace = nil

mountpoint = nil

begin
  Dir.mktmpdir("keywhiz-integration-test") do |dir|
    workspace = dir

    putsf "[*] Running tests in temporary directory #{dir}"

    # We depend on rspec being accessible in our shell.
    Kernel.system "rspec --help > /dev/null"
    raise RuntimeError, "Can't find spec in path", caller unless $? == 0

    Dir.chdir original_dir
    server_process = reset_db_and_execute_server(original_dir)

    # Once server is up (it will have run the migrations, created tables etc.).
    putsf "[*] Loading example data"
    Kernel.system "psql keywhiz_development -f server/src/test/resources/server_test_data.sql"

    mountpoint = File.join(dir, "mnt")
    Dir.chdir dir
    kwfs_process = checkout_keywhiz_fs(keywhiz_base_dir, mountpoint)

    putsf "[*] Running tests in directory #{Dir.getwd}"
    spec_file = "integration_tests_spec.rb"
    spec_path = File.join(keywhiz_base_dir, "tools", spec_file)
    raise RuntimeError unless File.exists? spec_path
    putsf "[*] Found spec file at #{spec_path}; copying to current directory."

    FileUtils.copy_file(spec_path, File.join(Dir.getwd, spec_file))

    # Make sure we leave some trace of spec's output.
    Kernel.system "rspec -b -f s #{spec_file}"
    raise RuntimeError unless $? == 0

    ret = 0
    putsf "[*] Spec complete. returned #{ret}"

    Kernel.system "umount #{mountpoint}"
    mountpoint = nil
    Dir.chdir original_dir
  end
ensure
  # TODO(justin): Would be nicer to SIGHUP first.

  putsf "[*] Umount kwfs"
  Kernel.system "umount #{mountpoint}" unless mountpoint.nil?

  putsf "[*] Killing server and kwfs processes"
  # kill the kwfs process.
  Process.kill("TERM", -Process.getpgid(kwfs_process.pid)) unless kwfs_process.nil? rescue ''
  sleep 2
  Process.kill("TERM", -Process.getpgid(server_process.pid)) unless server_process.nil? rescue ''

  # give our children two seconds to shutdown.
  sleep 2

  if not kwfs_process.nil?
    wait_result = Process.waitpid(kwfs_process.pid, Process::WNOHANG).nil? rescue ''
    # successful wait ?
    if not wait_result.nil?
      kwfs_process = nil
    end
  end
  sleep 2

  if not server_process.nil?
    wait_result = Process.waitpid(server_process.pid, Process::WNOHANG).nil? rescue ''
    # successful wait ?
    if not wait_result.nil?
      server_process = nil
    end
  end

  # Possibly we should wait for everyone to complete, ideally we'd
  # send a SIGKILL to the process-group -- but that will kill us too.
  #
  # Process.waitall rescue ''

  FileUtils.rm_rf(File.join(keywhiz_base_dir, "src/test/resources/certs_review/")) rescue ''
end

if ret == 0
   putsf "[*] SUCCESS"
end

exit! ret
