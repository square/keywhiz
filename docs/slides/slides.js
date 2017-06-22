function Slides(el) {
  Slides.instance = this;
  this.el = el;
  this.show(0);
}

Slides.data = [
  `<p>Keywhiz is a <strong>system for managing and distributing secrets</strong>. These
    short slides provides more details on what Keywhiz does, how it works and why you
    should use it.</p>
    <object data="slides/01_what_is_keywhiz_3.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a style="visibility: hidden" href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">1/15</div>
  `,
  `<h1>What is Keywhiz?</h1>
    <p>Keywhiz is is most useful when your server and network infrastructure grows and you
    need to ensure that sensitive files are always available on the right
    servers. In addition, Keywhiz provides an audit trail around these
    files.
    </p>
    <p>Keywhiz is an open source project by Square. It scales and is battle tested!</p>
    <p>The box below represents a Keywhiz server:</p>
    <object data="slides/01_what_is_keywhiz_1.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">2/15</div>
  `,
  `<h1>What is Keywhiz?</h1>
    <p>Keywhiz can securely store any type of file. It is usually used for application
    configurations, database credentials, GPG keys, TLS keys and certificates, API tokens, Kerberos keytabs, etc.</p>
    <p>Keywhiz secures the data at rest by encrypting it.</p>
    <object data="slides/01_what_is_keywhiz_2.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">3/15</div>
  `,
  `<h1>What is Keywhiz?</h1>
    <p>The data is distributed to Keywhiz clients using mTLS (TLS with mutual authentication).</p>
    <p>The Keywhiz clients exposes the data to applications running on their host without storing
    the files on disk.</p>
    <p>Keywhiz implements fine grained access controls. Each application-host pair can be granted
    access to specific files.</p>
    <object data="slides/01_what_is_keywhiz_3.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">4/15</div>
  `,
  `<h1>Why use Keywhiz</h1>
    <ul>
      <li>Common practices include putting secrets in config files next to code or copying files to servers
    out-of-band. The former is likely to be leaked and the latter difficult to track.</li>
      <li>Written in Java therefore supports Hardware Security Modules using Java Crypto Providers and PKCS11.</li>
      <li>Keywhiz integrates with your deployment system, making secret distribution reliable and secure.</li>
      <li>Deployed and proven to work at scale in Square production environments.</li>
    </ul>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">5/15</div>
  `,
  `<h1>Adding secrets</h1>
    <p>Let's go over a typical use-case: an administrator wants to add a secret to Keywhiz.</p>
    <p>The first step is to authenticate using a command line tool or the web interface.</p>
    <object data="slides/02_admin_authentication_1.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">6/15</div>
  `,
  `<h1>Adding secrets</h1>
    <p>The admin credentials are then authenticated using LDAP.</p>
    <p>Human operations can be automated using an automation API.</p>
    <p>note: the use of LDAP is optional but recommended. Keywhiz administrators can also be configured locally.</p>
    <object data="slides/02_admin_authentication_2.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">7/15</div>
  `,
  `<h1>Adding secrets</h1>
    <p>Once authenticated, the admin can add a files to Keywhiz or setup grants.</p>
    <object data="slides/03_admin_add_secret_1.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">8/15</div>
  `,
  `<h1>Adding secrets</h1>
    <p>The file is then encrypted using a Hardware Security Module (HSM).</p>
    <p>Using HSMs is optional, but strongly recommended. If you don’t have HSMs, you can configure a master
    key to encrypt files.</p>
    <object data="slides/03_admin_add_secret_2.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">9/15</div>
  `,
  `<h1>Adding secrets</h1>
    <p>The encrypted file is then stored in a database. We recommend using MySQL or H2. A large number of
    databases can be used by just regenerating jOOQ files, including SQLite, Microsoft Access 2013, Oracle 10g,
    SQL Server 2008 and Amazon Redshift.</p>
    <object data="slides/03_admin_add_secret_3.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">10/15</div>
  `,
  `<h1>Public key infrastructure</h1>
    <p>Keywhiz server and clients authenticate using a PKI.</p>
    <p>We recommend creating a unique x509 certificate for each application-host pair. Other
    configurations are possible.</p>
    <p>Keywhiz has been designed to work with short lived certificates; the code automatically
    detects and reloads when a certificate is renewed.</p>
    <object data="slides/04_pki.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">11/15</div>
  `,
  `<h1>Secret distribution</h1>
    <p>Keywhiz clients authenticate with their certificate and requests secrets.</p>
    <object data="slides/05_get_secret_1.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">12/15</div>
  `,
  `<h1>Secret distribution</h1>
    <p>The server fetches the encrypted data from the database.</p>
    <object data="slides/05_get_secret_2.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">13/15</div>
  `,
  `<h1>Secret distribution</h1>
    <p>The data is then decrypted using the HSM.</p>
    <object data="slides/05_get_secret_3.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">14/15</div>
  `,
  `<h1>Secret distribution</h1>
    <p>And finally sent to the client.</p>
    <p>At Square, we have served billions of secrets!</p>
    <object data="slides/05_get_secret_4.svg" type="image/svg+xml">
    </object>
    <div style="position: absolute; bottom: 2px; right: 2px">
    <a href="#" onclick="return slides_prev()"><img style="width: 30px" src="slides/prev.svg"></a>
    <a style="visibility: hidden" href="#" onclick="return slides_next()"><img style="width: 30px" src="slides/next.svg"></a>
    </div>
    <div style="position: absolute; bottom: 2px; left: 2px">15/15</div>
  `,
];

function slides_next() {
  Slides.instance.next();
  return false;
}

function slides_prev() {
  Slides.instance.prev();
  return false;
}


Slides.prototype.show = function(n) {
  this.current = n;
  this.el.html(Slides.data[n]);
}

Slides.prototype.next = function() {
  this.show(this.current+1);
}

Slides.prototype.prev = function() {
  this.show(this.current-1);
}

new Slides($('#slides'));
