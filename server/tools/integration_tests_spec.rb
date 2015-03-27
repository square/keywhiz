require 'json'
require 'addressable/uri'
require 'rest_client'
require 'etc'

describe "keywhiz kwfs" do
  context "retrieving a listing of accessible secrets" do
    it "should return 4 secrets" do
      @secrets = []
      Dir.entries("mnt").each do |f|
        if ! f.match(/^\./)
          @secrets << f
        end
      end
      @secrets.should be_an_instance_of Array
      @secrets.should have(4).items
    end

    it "should have the correct permissions for the root mountpoint" do
      mnt_st = File.stat("mnt")
      mnt_st.uid.should == Process.uid
      mnt_st.gid.should == Process.gid
      (mnt_st.mode & 0777).should == 0755
    end

    it "should have the correct permissions for .json subdirectories" do
      json_st = File.stat("mnt/.json")
      json_st.uid.should == Process.uid
      json_st.gid.should == Process.gid
      (json_st.mode & 0777).should == 0700

      secret_st = File.stat("mnt/.json/secret")
      secret_st.uid.should == Process.uid
      secret_st.gid.should == Process.gid
      (secret_st.mode & 0777).should == 0700
    end

    it "should have the correct permissions for .json file contents" do
      secretlist_st = File.stat("mnt/.json/secrets")
      secretlist_st.uid.should == Process.uid
      secretlist_st.gid.should == Process.gid
      (secretlist_st.mode & 0777).should == 0400

      secret_st = File.stat("mnt/.json/secret/Database_Password")
      secret_st.uid.should == Process.uid
      secret_st.gid.should == Process.gid
      (secret_st.mode & 0777).should == 0400
    end

    it "should contain only allowed secrets" do
      @secrets = []
      Dir.entries("mnt").each do |f|
        if ! f.match(/^\./)
          @secrets << f
        end
      end
      @secrets.should include "Database_Password"
      @secrets.should include "General_Password"
      @secrets.should_not include "Hacking_Password"
      @secrets.should include "Nobody_PgPass"
      @secrets.should include "NonexistentOwner_Pass"
    end
  end

  it "should optionally set mode and owner on the filesystem" do
    st = File.stat("mnt/Database_Password")
    st.uid.should == Process.uid
    (st.mode & 0777).should == 0440

    st = File.stat("mnt/Nobody_PgPass")
    (st.mode & 0777).should == 0400
    st.uid.should == Etc.getpwnam("nobody").uid
  end

  it "should assume the correct permissions on a non-existent overwritten user" do
    st = File.stat("mnt/NonexistentOwner_Pass")
    st.uid.should == Process.uid
    (st.mode & 0777).should == 0400
  end

  it "appears to be running" do
    File.exists?("mnt/.running").should be_true
  end

  it "appears to be communicating with the server" do
    rawlisting = ""
    lambda { rawlisting = File.read("mnt/.json/secrets") }.should_not raise_error
    rawlisting[0].ord.should equal('['[0].ord)
    rawlisting[-1].ord.should equal(']'[0].ord)
  end

  it "gets a secret" do
    secret = File.read("mnt/General_Password")
    secret.should == "asddas"
  end

  it "non-existent secrets don't allow access" do
    File.exists?("mnt/a_secret_which_does_not_exist").should be_false
  end
end
