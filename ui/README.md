# Keywhiz UI

The UI is an angular.js application. Keywhiz server delivers the
static ajax app to browser clients under /ui/.

## Development dependencies

Keywhiz UI uses [grunt](http://gruntjs.com/) as a task-runner and [bower](http://bower.io/) to download application dependencies. Both of these require the node package manager, [npm](https://npmjs.org/).

1. Install npm: `brew install node`
2. Install grunt & bower: `npm install -g grunt-cli bower`
3. Install dev modules: `npm install`
4. Download application dependencies: `bower install`
5. Try out full build: `grunt build`

## Development

One way to develop is to run the Keywhiz server, serving UI assets from the filesystem. Any changes to UI will be present on refresh and seed data is present.

1. Modify the Keywhiz server config to load UI assets from filesystem by uncommenting this line in keywhiz-development.yaml:
`alternateUiPath: ui/app/`
2. Run Keywhiz server: refer to server/README.md.
3. Visit [https://localhost:4444/ui/](https://localhost:4444/ui/)
4. Login with user: keywhizAdmin password: adminPass

## Build

`grunt build`

Will lint, test, minify, concatenate, version, and replace references to scripts and styles includes.

## Run tests

`grunt test`

Call karma directly for bare-bones tests execution. Install karma if necessary: `sudo npm install -g karma`
`karma start karma.conf.js --single-run`
`karma start karma-e2e.conf.js --single-run`

