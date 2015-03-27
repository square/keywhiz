'use strict';

module.exports = function (grunt) {
  // load all grunt tasks
  require('matchdep').filterDev('grunt-*').forEach(grunt.loadNpmTasks);

  // configurable paths
  var yeomanConfig = {
    app: 'app',
    dist: 'dist',
    test: 'test'
  };

  try {
    yeomanConfig.app = require('./bower.json').appPath || yeomanConfig.app;
  } catch (e) {}

  grunt.initConfig({
    yeoman: yeomanConfig,
    watch: {
      src: {
        files: [
          '<%= yeoman.app %>/{,*/}*.html',
          '<%= yeoman.app %>/styles/{,*/}*.css',
          '<%= yeoman.app %>/scripts/{,*/}*.js'
        ],
        tasks: ['build']
      },
      test: {
        files: [
          '<%= yeoman.test %>/{,*/}*.js'
        ],
        tasks: ['jshint', 'karma']
      }
    },
    clean: {
      options: {
        force: true // dist symlinks outside of CWD, so force delete
      },
      dist: {
        files: [{
          dot: true,
          src: [
            '<%= yeoman.dist %>/*',
            '!<%= yeoman.dist %>/.git*'
          ]
        }]
      }
    },
    jshint: {
      options: {
        jshintrc: '.jshintrc'
      },
      gruntfile: ['Gruntfile.js'],
      app: ['<%= yeoman.app %>/scripts/{,*/}*.js'],
      tests: ['<%= yeoman.test %>/{,*/}*.js']
    },
    karma: {
      unit: {
        configFile: 'karma.conf.js',
        singleRun: true
      }
    },
    useminPrepare: {
      html: '<%= yeoman.app %>/index.html',
      options: {
        dest: '<%= yeoman.dist %>'
      }
    },
    usemin: {
      html: ['<%= yeoman.dist %>/{,*/}*.html'],
      css: ['<%= yeoman.dist %>/styles/{,*/}*.css'],
      options: {
        dirs: ['<%= yeoman.dist %>']
      }
    },
    htmlmin: {
      dist: {
        options: {
          /*removeCommentsFromCDATA: true,
          // https://github.com/yeoman/grunt-usemin/issues/44
          //collapseWhitespace: true,
          collapseBooleanAttributes: true,
          removeAttributeQuotes: true,
          removeRedundantAttributes: true,
          useShortDoctype: true,
          removeEmptyAttributes: true,
          removeOptionalTags: true*/
        },
        files: [{
          expand: true,
          cwd: '<%= yeoman.app %>',
          src: ['*.html', 'partials/*.html'],
          dest: '<%= yeoman.dist %>'
        }]
      }
    },
    ngmin: {
      dist: {
        files: [{
          expand: true,
          cwd: '<%= yeoman.dist %>/scripts',
          src: '*.js',
          dest: '<%= yeoman.dist %>/scripts'
        }]
      }
    },
    uglify: {
      options: {
        mangle: false,
        report: 'min'
      }
    },
    rev: {
      dist: {
        files: {
          src: [
            '<%= yeoman.dist %>/scripts/{,*/}*.js',
            '<%= yeoman.dist %>/styles/{,*/}*.css'
          ]
        }
      }
    },
    copy: {
      fonts: {
        files: [{
          expand: true,
          cwd: '<%= yeoman.app %>',
          src: [
            'styles/glyphicons-halflings-regular.eot',
            'styles/glyphicons-halflings-regular.svg',
            'styles/glyphicons-halflings-regular.ttf',
            'styles/glyphicons-halflings-regular.woff',
            'styles/sqmarket-regular.eot',
            'styles/sqmarket-regular.ttf',
            'styles/sqmarket-regular.woff'
          ],
          dest: '<%= yeoman.dist %>'
        }]
      }
    }
  });

  grunt.registerTask('test', [
    'karma'
  ]);

  grunt.registerTask('build', [
    'clean:dist',
    'jshint',
    'test',
    'useminPrepare',
    'concat',
    'cssmin',
    'htmlmin',
    'copy:fonts',
    'ngmin',
    'uglify',
    'rev',
    'usemin'
  ]);

  grunt.registerTask('default', ['build']);
};
