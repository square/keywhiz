server:
  applicationConnectors:
    - type: https
      port: 4444
      keyStorePath: ${KEYSTORE_PATH}
      keyStorePassword: ${KEYSTORE_PASSWORD}
      keyStoreType: PKCS12
      trustStorePath: ${TRUSTSTORE_PATH} 
      trustStorePassword: ${TRUSTSTORE_PASSWORD}
      trustStoreType: PKCS12
      wantClientAuth: true
      # Dropwizard changed validatePeers and validateCerts defaults from true to false in 1.1.x
      validateCerts: true
      validatePeers: true
      enableCRLDP: false
      enableOCSP: false
      crlPath: ${CRL_PATH}
      supportedProtocols: [TLSv1.2]
      supportedCipherSuites:
        - TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256
        - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
        - TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256
        - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
        - TLS_RSA_WITH_AES_128_CBC_SHA256
        - TLS_RSA_WITH_AES_128_GCM_SHA256
        - TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256
        - TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256
        - TLS_DHE_RSA_WITH_AES_128_CBC_SHA256
        - TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA
        - TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA
        - TLS_RSA_WITH_AES_128_CBC_SHA
        - TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA
        - TLS_ECDH_RSA_WITH_AES_128_CBC_SHA
        - TLS_DHE_RSA_WITH_AES_128_CBC_SHA
  adminConnectors:
    - type: http
      port: 8085

logging:
  appenders:
    - type: console
      threshold: ALL

environment: docker

database:
  driverClass: com.mysql.cj.jdbc.Driver
  url: jdbc:mysql://localhost/keywhizdb_docker?useUnicode=true&characterEncoding=utf8
  user: root
  properties:
    charSet: UTF-8
  initialSize: 2
  minSize: 2
  maxSize: 2
  # There is explicitly no password. Do not uncomment.
  # password:

readonlyDatabase:
  driverClass: com.mysql.cj.jdbc.Driver
  url: jdbc:mysql://localhost/keywhizdb_docker?useUnicode=true&characterEncoding=utf8
  user: root
  properties:
    charSet: UTF-8
  readOnlyByDefault: true
  initialSize: 1
  minSize: 1
  maxSize: 1
  # There is explicitly no password. Do not uncomment.
  # password:

migrationsDir:
  db/mysql/migration

userAuth:
  type: bcrypt

cookieKey: external:${COOKIE_KEY_PATH}

sessionCookie:
  name: session
  path: /admin

contentKeyStore:
  path: ${CONTENT_KEYSTORE_PATH}
  type: JCEKS
  password: ${CONTENT_KEYSTORE_PASSWORD}
  alias: basekey
