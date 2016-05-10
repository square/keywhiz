/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package keywhiz.auth.ldap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** This class wraps an SSLSocketFactory and sets the Endpoint Identification algorithm to
 * LDAP.  This is important for connecting to TLS through a load balancer, where we may see
 * different certs, triggering Java8's 3shake mitigation */
final class EndpointIdentificationSocketFactory extends SocketFactory {
  private final SSLSocketFactory factory;

  EndpointIdentificationSocketFactory(SSLSocketFactory factory) {
    this.factory = factory;
  }

  private Socket setEndpoint(Socket socket) {
    SSLSocket sslSocket = (SSLSocket)socket;
    SSLParameters parameters = sslSocket.getSSLParameters();
    parameters.setEndpointIdentificationAlgorithm("LDAPS");
    sslSocket.setSSLParameters(parameters);
    return sslSocket;
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    return setEndpoint(factory.createSocket(host, port));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
      throws IOException {
    return setEndpoint(factory.createSocket(host, port, localAddress, localPort));
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int port) throws IOException {
    return setEndpoint(factory.createSocket(inetAddress, port));
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int port, InetAddress localAddress, int localPort)
      throws IOException {
    return setEndpoint(factory.createSocket(inetAddress, port, localAddress, localPort));
  }
}