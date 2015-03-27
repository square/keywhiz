'use strict';

/* Controllers */
angular.module('keywhizUi.controllers', ['ngRoute'])
  .value('statusMessageTimeoutMillis', 3000)
  .value('mysqlUsers', ['nrpe', 'mysql_ro', 'percona', 'root', 'xtrabackup'])

  .controller('NavPillCtrl', function($scope) {
    $scope.pill = { active: 'secrets' };
    $scope.pages = [
      { path: 'secrets', title: 'Secrets' },
      { path: 'groups', title: 'Groups' },
      { path: 'clients', title: 'Clients' },
      { path: 'transcript', title: 'Transcript' }
    ];
  })

  .controller('StatusMessageCtrl', function($scope, $timeout, statusMessageTimeoutMillis) {
    $scope.alerts = [];
    $scope.timeout = null;

    $scope.closeAlert = function(index) {
      $scope.alerts.splice(index, 1);
    };

    $scope.$on('event:status-alert', function(ev, options) {
      $scope.alerts.push(options);

      if (!options.timeoutMillis) {
        options.timeoutMillis = statusMessageTimeoutMillis;
      }

      $timeout.cancel($scope.timeout);
      $scope.timeout = $timeout(function () {
        $scope.alerts.pop();
      }, options.timeoutMillis);
    });
  })

  .controller('LoginCtrl', function($scope, Session) {
    $scope.errorMessage = '';
    Session.current().then(function(result) {
      $scope.username = result;
    });

    $scope.submit = function() {
      Session.login($scope.login).then(function() {
        $scope.errorMessage = '';
      }, function(error) {
        $scope.errorMessage = error;
      });

      $scope.login = null;
    };

    $scope.logout = function() {
      Session.logout();
      $scope.username = '';
    };
  })

  .controller('ClientListCtrl', function($scope, Client) {
    $scope.sort = 'name'; // Default row sort
    $scope.clients = Client.query();
  })

  .controller('ClientDetailCtrl', function($scope, $routeParams, Client, ClientMembership, Group, Timestamp, Transcript,
                                           Undo) {
    $scope.selected = undefined;
    $scope.client = Client.get({clientId: $routeParams.clientId});
    $scope.groups = Group.query();

    function updateClient() {
      $scope.client = Client.get({clientId: $scope.client.id});
    }

    $scope.undo = function() {
      Undo.undo();
    };

    $scope.assignGroup = function() {
      var group;
      $scope.groups.map(function(g) {
        if (g.name === $scope.selected) {
          group = g;
        }
      });
      $scope.selected = undefined;

      if (group) {
        ClientMembership.assign($scope.client.id, group.id, function() {
          var message = 'Assigned client \'' + $scope.client.name + '\' from group \'' + group.name + '\'.';
          Transcript.log(Timestamp.short() + ': ' + message);

          Undo.updateUndo(function() {
            $scope.unassignGroup(group);
          }, message, $scope);

          $scope.group = null;
          updateClient();
        });
      }
    };

    $scope.unassignGroup = function(group) {
      if (group) {
        ClientMembership.unassign($scope.client.id, group.id, function() {
          var message = 'Unassigned client \'' + $scope.client.name + '\' from group \'' + group.name + '\'.';
          Transcript.log(Timestamp.short() + ': ' + message);

          Undo.updateUndo(function() {
            $scope.selected = group.name;
            $scope.assignGroup();
          }, message, $scope);

          updateClient();
        });
      }
    };
  })

  .controller('GroupCreateCtrl', function($scope, $rootScope, $modalInstance, ErrorMessage, Group, Timestamp, Transcript) {
    $scope.close = function(result) {
      $modalInstance.close(result);
    };

    $scope.create = function(name) {
      var group = new Group();
      group.name = name;

      group.$save(function() {
        Transcript.log(Timestamp.short() + ': Created group\'' + group.name + '\'.');
        $rootScope.$broadcast('event:status-alert', {
          message: 'Group \'' + group.name + '\' created successfully.',
          type: 'success'
        });
      }, function(data) {
        $rootScope.$broadcast('event:status-alert', {
          message: 'Server response: ' + ErrorMessage.extract(data),
          type: 'error'
        });
      });

      $modalInstance.close(true);
    };
  })

  .controller('GroupListCtrl', function($scope, $modal, Group) {
    $scope.sort = 'name'; // Default row sort
    $scope.groups = Group.query();

    var dialogOpts = {
      backdrop: true,
      dialogFade: true,
      backdropFade: true,
      keyboard: true,
      backdropClick: true,
      templateUrl: 'partials/groupCreate.html',
      controller: 'GroupCreateCtrl'
    };

    $scope.openDialog = function() {
      var d = $modal.open(dialogOpts);
      d.result.then(function(result) {
        // Only refresh if result true, indicating update.
        if (result) {
          $scope.groups = Group.query();
        }
      });
    };
  })

  .controller('GroupDetailCtrl', function($scope, $rootScope, $routeParams, $modal, Group, Secret, Client,
                                          ClientMembership, SecretMembership, Timestamp, Transcript, Undo) {
    $scope.selectedClient = undefined;
    $scope.selectedSecret = undefined;
    $scope.group = Group.get({groupId: $routeParams.groupId});
    $scope.clients = Client.query();
    $scope.secrets = Secret.query();

    function updateGroup() {
      $scope.group = Group.get({groupId: $scope.group.id});
    }

    $scope.undo = function() {
      Undo.undo();
    };

    $scope.openDeleteDialog = function() {
      $modal.open({
        templateUrl: 'partials/groupDelete.html',
        controller:  'GroupDeleteCtrl'
      });
    };

    $scope.assignClient = function() {
      var client;
      $scope.clients.map(function(c) {
        if (c.name === $scope.selectedClient) {
          client = c;
        }
      });
      $scope.selectedClient = undefined;

      if (client) {
        ClientMembership.assign(client.id, $scope.group.id, function() {
          var message = 'Assigned client \'' + client.name + '\' from group \'' + $scope.group.name + '\'.';
          Transcript.log(Timestamp.short() + ': ' + message);

          Undo.updateUndo(function() {
            $scope.unassignClient(client);
          }, message, $scope);

          $scope.client = null;
          updateGroup();
        });
      }
    };

    $scope.unassignClient = function(client) {
      if (client) {
        ClientMembership.unassign(client.id, $scope.group.id, function() {
          var message = 'Unassigned client \'' + client.name + '\' from group \'' + $scope.group.name + '\'.';
          Transcript.log(Timestamp.short() + ': ' + message);

          Undo.updateUndo(function() {
            $scope.selectedClient = client.name;
            $scope.assignClient();
          }, message, $scope);

          updateGroup();
        });
      }
    };

    $scope.allowSecret = function() {
      var secret;
      $scope.secrets.map(function(s) {
        if (s.name === $scope.selectedSecret) {
          secret = s;
        }
      });
      $scope.selectedSecret = undefined;

      if (secret) {
        SecretMembership.assign(secret.id, $scope.group.id, function() {
          var message = 'Assigned group \'' + $scope.group.name + '\' access to secret \'' + secret.name + '\'.';
          Transcript.log(Timestamp.short() + ': ' + message);

          Undo.updateUndo(function() {
            $scope.disallowSecret(secret);
          }, message, $scope);

          $scope.secret = null;
          updateGroup();
        });
      }
    };

    $scope.disallowSecret = function(secret) {
      if (secret) {
        SecretMembership.unassign(secret.id, $scope.group.id, function() {
          var message = 'Unassigned group \'' + $scope.group.name + '\' access to secret \'' + secret.name + '\'.';
          Transcript.log(Timestamp.short() + ': ' + message);

          Undo.updateUndo(function() {
            $scope.selectedSecret = secret.name;
            $scope.allowSecret();
          }, message, $scope);

          updateGroup();
        });
      }
    };
  })

  .controller('GroupDeleteCtrl', function($scope, $rootScope, $routeParams, $modalInstance, ErrorMessage, Group) {
    $scope.group = Group.get({groupId: $routeParams.groupId});

    $scope.close = function(result) {
      $modalInstance.close(result);
    };

    $scope.deleteGroup = function () {
      $scope.close();
      Group.delete({groupId: $routeParams.groupId}, function() {
        $rootScope.$broadcast('event:status-alert', {
          message: 'Group ' + $scope.group.name + ' deleted successfully.',
          type: 'success'
        });
      }, function(data) {
        $rootScope.$broadcast('event:status-alert', {
          message: ErrorMessage.extract(data),
          type: 'error'
        });
      });
    };
  })

  .controller('SecretCreateCtrl', function($scope, $modalInstance) {
    $scope.close = function(result) {
      $modalInstance.close(result);
    };

    $scope.tabs = [
      { label: 'Database', partial: 'partials/databaseSecretCreate.html', active: true },
      { label: 'Mysql Cluster', partial: 'partials/mysqlClusterSecretCreate.html' },
      { label: 'Postgres Cluster', partial: 'partials/postgresClusterSecretCreate.html' },
      { label: 'Rails Token', partial: 'partials/railsSecretTokenCreate.html'},
      { label: 'Upload', partial: 'partials/fileSecretCreate.html' }
    ];
  })

  .controller('DatabaseSecretCreateCtrl', function($scope, $rootScope, $http, ErrorMessage, Timestamp, Transcript) {
    $scope.create = function(description, ou) {
      var secretName = ou + '-database.yaml';
      var secretTemplate = {
        name: secretName,
        template: [
          '---',
          'datasources:',
          '  ' + ou + ':',
          '    password: DBYAML_GEN_PASSWORD{{#alphanumeric}}32{{/alphanumeric}}'
        ].join('\n'),
        description: description,
        withVersion: false,
        metadata: {}
      };

      $http.post('/admin/secrets/generators/templated', secretTemplate, {})
        .success(function() {
          Transcript.log(Timestamp.short() + ': Created secret\'' + secretTemplate.name + '\'.');
          $rootScope.$broadcast('event:status-alert', {
            message: 'Secret \'' + secretTemplate.name + '\' created successfully.',
            type: 'success'
          });
        })
        .error(function(data) {
          $rootScope.$broadcast('event:status-alert', {
            message: 'Server response: ' + ErrorMessage.extract(data),
            type: 'error'
          });
        });

      $scope.close(true);
    };
  })

  .controller('MysqlClusterSecretCreateCtrl', function($scope, $rootScope, $http, ErrorMessage, Timestamp, Transcript,
                                                       mysqlUsers) {
    $scope.create = function(name, description) {
      var secretTemplates = [];
      mysqlUsers.map(function(username) {
        var metadata = {};

        if (username === 'nrpe') {
          metadata = { owner: 'nagios', mode: '0400'};
        } else if (username === 'xtrabackup') {
          metadata = { owner: 'dbbackup', mode: '0400'};
        }

        secretTemplates.push({
          name: 'my_' + username + '_' + name + '.cnf',
          template: [
            '[client]',
            '',
            'user     = ' + username,
            'password = DBYAML_GEN_PASSWORD{{#alphanumeric}}32{{/alphanumeric}}'
          ].join('\n'),
          description: description,
          withVersion: false,
          metadata: metadata
        });
      });

      secretTemplates.push({
        name: 'replication_pass_' + name,
        template: '{{#alphanumeric}}32{{/alphanumeric}}',
        description: description,
        withVersion: false,
        metadata: {}
      });

      $http.post('/admin/secrets/generators/templated/batch', secretTemplates, {})
        .success(function() {
          Transcript.log(Timestamp.short() + ': Created secrets for cluster \'' + name + '\'.');
          $rootScope.$broadcast('event:status-alert', {
            message: 'Cluster \'' + name + '\' created successfully.',
            type: 'success'
          });
        })
        .error(function(data) {
          $rootScope.$broadcast('event:status-alert', {
            message: 'Server response: ' + ErrorMessage.extract(data),
            type: 'error'
          });
        });

      $scope.close(true);
    };
  })

  .controller('PostgresClusterSecretCreateCtrl', function($scope, $rootScope, $http, ErrorMessage, Timestamp,
                                                          Transcript) {
    function template(postgresUsername, hostUsername, clusterName, description) {
      return {
        name: 'pgpass_' + postgresUsername + '_' + clusterName,
        template: '*:5432:*:' + postgresUsername + ':PG_GEN_PASSWORD{{#alphanumeric}}32{{/alphanumeric}}',
        description: description,
        withVersion: false,
        metadata: { owner: hostUsername, group: hostUsername, mode: '0400'}
      };
    }

    $scope.create = function(name, description) {
      var secretTemplates = [
        template('postgres', 'postgres', name, description),
        template('nrpe', 'nagios', name, description),
        {
          name: 'replication_pass_' + name,
          template: '{{#alphanumeric}}32{{/alphanumeric}}',
          description: description,
          withVersion: false,
          metadata: {}
        }
      ];

      $http.post('/admin/secrets/generators/templated/batch', secretTemplates, {})
        .success(function() {
          Transcript.log(Timestamp.short() + ': Created secrets for cluster \'' + name + '\'.');
          $rootScope.$broadcast('event:status-alert', {
            message: 'Cluster \'' + name + '\' created successfully.',
            type: 'success'
          });
        })
        .error(function(data) {
          $rootScope.$broadcast('event:status-alert', {
            message: 'Server response: ' + ErrorMessage.extract(data),
            type: 'error'
          });
        });

      $scope.close(true);
    };
  })

  .controller('RailsSecretTokenCtrl', function($scope, $rootScope, $http, ErrorMessage, Timestamp, Transcript) {
    function buildRequest(groupName) {
      return {
        name: groupName + '-rails-secret-token',
        template: '{{#hexadecimal}}128{{/hexadecimal}}',
        description: 'Random 512-bit hexadecimal string (128 chars) for rails secret token',
        withVersion: false
      };
    }

    $scope.create = function(groupName) {
      var request = buildRequest(groupName);
      $http.post('/admin/secrets/generators/templated', request, {})
        .success(function() {
          Transcript.log(Timestamp.short() + ': Created rails secret_token for \'' + groupName + '\'.');
          $rootScope.$broadcast('event:status-alert', {
            message: 'Rails secret_token for \'' + groupName + '\' created successfully.',
            type: 'success'
          });
        })
        .error(function(error) {
          $rootScope.$broadcast('event:status-alert', {
            message: 'Server response: ' + ErrorMessage.extract(error),
            type: 'error'
          });
        });

      $scope.close(true);
    };
  })

  .controller('FileSecretCreateCtrl', function($scope, $rootScope, Secret, $filter, ErrorMessage, Timestamp,
                                               Transcript) {
    var base64delimiter = ';base64,';
    var maximumFileSize = 1024 * 100;
    var number = $filter('number');

    $scope.readFile = function(file) {
      // Not a real validation on filesize (that is server-side), but prevents loading a large file as a huge base64 string.
      if (file.size > maximumFileSize) {
        $rootScope.$broadcast('event:status-alert', {
          message: 'File of ' + number(file.size) + ' bytes exceeds ' + number(maximumFileSize) + ' byte limit.',
          type: 'error'
        });
        return;
      }

      var reader = new FileReader();

      reader.onload = function(evt) {
        // Result has form data:text/plain;base64,[base64 content], although the MIME type may vary.
        var delimiterPosition = evt.target.result.search(base64delimiter);
        if (delimiterPosition >= 0) {
          $scope.content = evt.target.result.substring(delimiterPosition + base64delimiter.length);
        }
      };

      reader.readAsDataURL(file);
    };

    $scope.create = function(name, description, metadata) {
      if (!$scope.content) {
        $rootScope.$broadcast('event:status-alert', {
          message: 'Please choose a file',
          type: 'error'
        });
        return;
      }

      var secretMetadata = {};
      try {
        if (metadata) {
          secretMetadata = JSON.parse(metadata);
        }
      } catch(e) {
        $rootScope.$broadcast('event:status-alert', {
          message: 'JSON parsing error: ' + e.message,
          type: 'error'
        });
        return;
      }

      var secret = new Secret({
        name: name,
        content: $scope.content,
        description: description,
        withVersion: false,
        metadata: secretMetadata
      });

      secret.$save(function() {
        Transcript.log(Timestamp.short() + ': Created secret\'' + name + '\'.');
        $rootScope.$broadcast('event:status-alert', {
          message: 'Secret \'' + name + '\' created successfully.',
          type: 'success'
        });
      }, function(data) {
        $rootScope.$broadcast('event:status-alert', {
          message: 'Server response: ' + ErrorMessage.extract(data),
          type: 'error'
        });
      });
      $scope.content = undefined;
      $scope.close(true);
    };
  })

  .controller('SecretListCtrl', function($scope, $modal, Secret) {
    $scope.sort = 'name'; // Default row sort
    $scope.secrets = Secret.query();

    var dialogOpts = {
      backdrop: true,
      keyboard: true,
      templateUrl: 'partials/secretCreate.html',
      controller: 'SecretCreateCtrl'
    };

    $scope.openDialog = function() {
      var d = $modal.open(dialogOpts);
      d.result.then(function(result) {
        // Only refresh if result true, indicating update.
        if (result) {
          $scope.secrets = Secret.query();
        }
      });
    };
  })

  .controller('SecretDetailCtrl', function($scope, $rootScope, $routeParams, $modal, ErrorMessage, Secret,
                                           Group, SecretMembership, Timestamp, Transcript, Undo) {
    $scope.selected = undefined;
    $scope.secret = Secret.get({secretId: $routeParams.secretId});
    $scope.groups = Group.query();

    function updateSecret() {
      $scope.secret = Secret.get({secretId: $scope.secret.id});
    }

    $scope.undo = function() {
      Undo.undo();
    };

    $scope.openDeleteConfirmation = function() {
      $modal.open({
        templateUrl: 'partials/secretDelete.html',
        controller:  'SecretDeleteCtrl'
      });
    };

    $scope.assignGroup = function() {
      var group;
      ($scope.groups || []).map(function(g) {
        if (g.name === $scope.selected) {
          group = g;
        }
      });
      $scope.selected = undefined;

      if (group) {
        SecretMembership.assign($scope.secret.id, group.id, function() {
          var message = 'Assigned group \'' + group.name + '\' access to secret \'' + $scope.secret.name + '\'.';
          Transcript.log(Timestamp.short() + ': ' + message);

          Undo.updateUndo(function() {
            $scope.unassignGroup(group);
          }, message, $scope);

          $scope.group = null;
          updateSecret();
        });
      }
    };

    $scope.unassignGroup = function(group) {
      if (group) {
        SecretMembership.unassign($scope.secret.id, group.id, function() {
          var message = 'Unassigned group \'' + group.name + '\' access to secret \'' + $scope.secret.name + '\'.';
          Transcript.log(Timestamp.short() + ': ' + message);

          Undo.updateUndo(function() {
            $scope.selected = group.name;
            $scope.assignGroup();
          }, message, $scope);

          updateSecret();
        });
      }
    };
  })

  .controller('SecretDeleteCtrl', function($scope, $rootScope, $routeParams, $modalInstance, $location, ErrorMessage, Secret) {
    $scope.secret = Secret.get({secretId: $routeParams.secretId});

    $scope.close = function(result) {
      $modalInstance.close(result);
    };

    $scope.deleteSecret = function() {
      $scope.close();
      Secret.delete({secretId: $scope.secret.id}, function () {
        $rootScope.$broadcast('event:status-alert', {
          message: 'Secret ' + $scope.secret.name + ' deleted successfully.',
          type: 'success'
        });
        $location.path('/secrets'); // Navigate to listing after deletion.
      }, function(data) {
        $rootScope.$broadcast('event:status-alert', {
          message: 'There was an error deleting ' + $scope.secret.name + '. ' + ErrorMessage.extract(data),
          type: 'error'
        });
      });
    };
  })

  .controller('TranscriptCtrl', function($scope, Transcript) {
    function updateEvents() {
      $scope.events = Transcript.log().reverse();
      if ($scope.events.length === 0) {
        $scope.events = ['Empty'];
      }
    }

    updateEvents();

    $scope.Transcript = Transcript;
    // Evaluates expression on $scope when $digest event occurs.
    $scope.$watch('Transcript.log()', function() {
      updateEvents();
    }, true);
  });
