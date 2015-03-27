'use strict';

/* jasmine specs for controllers go here */
describe('KeywhizUI Controllers', function() {
  beforeEach(module('keywhizUi.controllers'));

  beforeEach(function() {
    this.addMatchers({
      toEqualData: function(expected) {
        return angular.equals(this.actual, expected);
      }
    });

    module('keywhizUi.services', function($provide) {
      $provide.value('Transcript', {
        log: jasmine.createSpy('log')
      });
    });

    module('http-auth-interceptor', function($provide) {
      $provide.value('authService', {
        loginConfirmed: jasmine.createSpy('loginConfirmed')
      });
    });
  });

  describe('statusMessageTimeoutMillis', function() {
    it('should be positive', inject(function(statusMessageTimeoutMillis) {
      expect(statusMessageTimeoutMillis).toBeGreaterThan(0);
    }));
  });

  describe('StatusMessageCtrl', function() {
    var scope, ctrl, rootScope;

    beforeEach(inject(function($rootScope, $controller) {
      scope = $rootScope.$new();
      rootScope = $rootScope;
      ctrl = $controller('StatusMessageCtrl', {$scope: scope});
    }));

    describe('status message', function() {
      it('should change message and type on receiving event', function() {
        var alert = {
          message: 'Status',
          type: 'success'
        };

        rootScope.$broadcast('event:status-alert', alert);
        expect(scope.alerts).toContain(alert);
      });

      it('should store multiple status messages', function() {
        var alert1 = {
            message: 'Status One',
            type: 'success'
          },
          alert2 = {
            message: 'Status Two',
            type: 'error'
          };

        rootScope.$broadcast('event:status-alert', alert1);
        rootScope.$broadcast('event:status-alert', alert2);
        expect(scope.alerts).toEqual([alert1, alert2]);
      });

      it('should make the status message disappear on timeout', inject(function($timeout) {
        var alert = {
          message: 'Status Two',
          type: 'error',
          timeoutMillis: 1
        };

        rootScope.$broadcast('event:status-alert', alert);
        expect(scope.alerts).toContain(alert);
        $timeout.flush();
        expect(scope.alerts).not.toContain(alert);
      }));
    });
  });

  describe('ClientListCtrl', function() {
    var scope, ctrl, Client,
      listingData = function() {
        return [
          {
            'id': 9875,
            'name': 'client 1',
            'description': 'client 1 description',
            'createdAt': '2012-08-01T13:15:30.000Z',
            'createdBy': 'user',
            'updatedAt': '2012-08-01T13:15:30.000Z',
            'updatedBy': 'user',
            'enabled': true
          },
          {
            'id': 9876,
            'name': 'client 2',
            'description': 'client 2 description',
            'createdAt': '2012-08-01T13:15:30.000Z',
            'createdBy': 'user',
            'updatedAt': '2012-08-01T13:15:30.000Z',
            'updatedBy': 'user',
            'enabled': true
          },
          {
            'id': 9877,
            'name': 'client 3',
            'description': 'client 3 description',
            'createdAt': '2012-08-01T13:15:30.000Z',
            'createdBy': 'user',
            'updatedAt': '2012-08-01T13:15:30.000Z',
            'updatedBy': 'user',
            'enabled': true
          }
        ];
      };

    beforeEach(inject(function($rootScope, $controller) {
      Client = jasmine.createSpyObj('Client', ['query']);
      Client.query.andCallFake(listingData);

      scope = $rootScope.$new();

      ctrl = $controller('ClientListCtrl', {
        $scope: scope,
        Client: Client
      });
    }));

    it('should create clients model', function(){
      expect(scope.clients).toEqualData(listingData());
    });
  });

  describe('ClientDetailCtrl', function() {
    var scope, ctrl, Client, ClientMembership, Group,
      clientId = 9875,
      appHostsGroup = {'id': 111, 'name': 'app_hosts'},
      apiHostsGroup = {'id': 112, 'name': 'api_hosts'},
      webGroup = {'id': 113, 'name': 'web'},
      detailData = function() {
        return {
          'id': clientId,
          'name': 'client 1',
          'description': 'client 1 description',
          'createdAt': '2012-08-01T13:15:30.000Z',
          'createdBy': 'user',
          'updatedAt': '2012-09-10T03:15:30.000Z',
          'updatedBy': 'user',
          'enabled': true,
          'groups': [appHostsGroup, apiHostsGroup]
        };
      };

    beforeEach(inject(function($rootScope, $routeParams, $controller) {
      Group = jasmine.createSpyObj('Group', ['query']);
      Group.query.andCallFake(function() {
        return [appHostsGroup, apiHostsGroup, webGroup];
      });

      Client = jasmine.createSpyObj('Client', ['get']);
      Client.get.andCallFake(detailData);

      ClientMembership = jasmine.createSpyObj('ClientMembership', ['assign', 'unassign']);

      $routeParams.clientId = clientId;
      scope = $rootScope.$new();

      ctrl = $controller('ClientDetailCtrl', {
        $scope: scope,
        Client: Client,
        ClientMembership: ClientMembership,
        Group: Group
      });
    }));

    it('should create client model', function() {
      expect(Client.get).toHaveBeenCalledWith({clientId: clientId});
      expect(scope.client).toEqualData(detailData());
    });

    describe('on group assignment', function() {
      describe('when successful', function() {
        beforeEach(function() {
          ClientMembership.assign.andCallFake(function(clientId, groupId, success) {
            success();
          });

          scope.selected = webGroup.name;
          scope.assignGroup();
        });

        it('should call ClientMembership.assign', function() {
          expect(ClientMembership.assign)
            .toHaveBeenCalledWith(clientId, webGroup.id, jasmine.any(Function));
        });

        it('should log to the transcript', inject(function(Transcript) {
          expect(Transcript.log).toHaveBeenCalled();
        }));
      });

      it('should not call ClientMembership.assign when the group is undefined', function() {
        scope.assignGroup();
        expect(ClientMembership.assign).not.toHaveBeenCalled();
      });
    });

    describe('on group unassignment', function() {
      describe('when successful', function() {
        beforeEach(function() {
          ClientMembership.unassign.andCallFake(function(clientId, groupId, success) {
            success();
          });

          scope.unassignGroup(appHostsGroup);
        });

        it('should call ClientMembership.unassign', function() {
          expect(ClientMembership.unassign)
            .toHaveBeenCalledWith(clientId, appHostsGroup.id, jasmine.any(Function));
        });

        it('should log to the transcript', inject(function(Transcript) {
          expect(Transcript.log).toHaveBeenCalled();
        }));
      });

      it('should not call ClientMembership.unassign when the group is undefined', function() {
        scope.unassignGroup();
        expect(ClientMembership.unassign).not.toHaveBeenCalled();
      });
    });
  });

  describe('GroupCreateCtrl', function() {
    var scope, ctrl, mock$ModalInstance, statusAlertListener, Group;

    beforeEach(function() {
      statusAlertListener = jasmine.createSpy('statusAlertListener');
      Group = jasmine.createSpyObj('Group', ['$save']);

      mock$ModalInstance = {close: jasmine.createSpy('close')};
      module(function($provide) {
        $provide.value('$modalInstance', mock$ModalInstance);
      });
    });

    beforeEach(inject(function($rootScope, $routeParams, $controller) {
      scope = $rootScope.$new();
      scope.$on('event:status-alert', statusAlertListener);

      ctrl = $controller('GroupCreateCtrl', {
        $scope: scope,
        Group: function() { return Group; }
      });
    }));

    it('should set the status message upon error', function() {
      Group.$save.andCallFake(function(success, fail) {
        fail();
      });

      scope.create('fakeGroup');
      expect(Group.name).toEqual('fakeGroup');
      expect(scope.errorMessage).not.toEqual('');
    });

    it('should update the status with the message from the server', function() {
      Group.$save.andCallFake(function(success, fail) {
        fail({success: false, message: 'Cannot create group.', debugInfo: ''});
      });

      scope.create('fakeGroup');
      expect(Group.name).toEqual('fakeGroup');
      expect(statusAlertListener).toHaveBeenCalled();
    });

    describe('when successful', function() {
      it('should log to the transcript', inject(function(Transcript) {
        Group.$save.andCallFake(function(success) {
          success();
        });

        scope.create('fakeGroup');
        expect(Group.name).toEqual('fakeGroup');
        expect(Transcript.log).toHaveBeenCalled();
      }));
    });
  });

  describe('GroupListCtrl', function() {
    var scope, ctrl, mock$Modal, Group,
      listingData = function() {
        return [
          {
            'id':765,
            'name':'group1',
            'createdAt': '2011-08-01T13:15:30.000Z',
            'createdBy': 'other-user',
            'updatedAt': '2011-08-01T13:15:30.000Z',
            'updatedBy': 'other-user'
          },
          {
            'id':234,
            'name':'group2',
            'createdAt': '2012-08-01T13:15:30.000Z',
            'createdBy': 'user',
            'updatedAt': '2012-09-10T03:15:30.000Z',
            'updatedBy': 'other-user'
          }
        ];
      };

    beforeEach(function() {
      Group = jasmine.createSpyObj('Group', ['query']);
      Group.query.andCallFake(listingData);

      mock$Modal = {open: jasmine.createSpy('open')};
      module(function($provide) {
        $provide.value('$modal', mock$Modal);
      });
    });

    beforeEach(inject(function($rootScope, $controller) {
      scope = $rootScope.$new();

      ctrl = $controller('GroupListCtrl', {
        $scope: scope,
        Group: Group
      });
    }));

    it('should create groups model with 2 entries fetched via xhr', function() {
      expect(scope.groups).toEqualData(listingData());
    });
  });

  describe('GroupDetailCtrl', function() {
    var scope, ctrl, Client, ClientMembership, Group, Secret, SecretMembership, mock$Modal,
      app01Client = {'id': 1283, 'name': 'app01'},
      app02Client = {'id': 1284, 'name': 'app02'},
      nobodyPgpassSecret = {'id': 737, 'name': 'Nobody_PgPass'},
      generalPasswordSecret = {'id': 740, 'name': 'General_Password'},
      detailData = function() {
        return {
          'id':234,
          'name':'group2',
          'createdAt': '2012-08-01T13:15:30.000Z',
          'createdBy': 'user',
          'updatedAt': '2012-09-10T03:15:30.000Z',
          'updatedBy': 'other-user',
          'clients': [app01Client],
          'secrets': [generalPasswordSecret]
        };
      };

    beforeEach(inject(function($rootScope, $routeParams, $controller) {
      Group = jasmine.createSpyObj('Group', ['get']);
      Group.get.andCallFake(detailData);

      Client = jasmine.createSpyObj('Client', ['query']);
      Client.query.andCallFake(function() {
        return [app01Client, app02Client];
      });

      Secret = jasmine.createSpyObj('Secret', ['query']);
      Secret.query.andCallFake(function() {
        return [nobodyPgpassSecret, generalPasswordSecret];
      });

      ClientMembership = jasmine.createSpyObj('ClientMembership', ['assign', 'unassign']);
      SecretMembership = jasmine.createSpyObj('SecretMembership', ['assign', 'unassign']);

      $routeParams.groupId = 234;
      scope = $rootScope.$new();

      mock$Modal = {open: jasmine.createSpy('open')};

      ctrl = $controller('GroupDetailCtrl', {
        $modal: mock$Modal,
        $scope: scope,
        Client: Client,
        ClientMembership: ClientMembership,
        Group: Group,
        Secret: Secret,
        SecretMembership: SecretMembership
      });
    }));

    it('should create group model', function() {
      expect(Group.get).toHaveBeenCalledWith({groupId: 234});
      expect(scope.group).toEqualData(detailData());
    });

    it('should create clients model', function() {
      expect(Client.query).toHaveBeenCalled();
      expect(scope.clients).toEqualData([app01Client, app02Client]);
    });

    it('should create secrets model', function() {
      expect(Secret.query).toHaveBeenCalled();
      expect(scope.secrets).toEqualData([nobodyPgpassSecret, generalPasswordSecret]);
    });

    describe('on client assignment', function() {
      describe('when successful', function() {
        beforeEach(function() {
          ClientMembership.assign.andCallFake(function(clientId, groupId, success) {
            success();
          });

          scope.selectedClient = app02Client.name;
          scope.assignClient();
        });

        it('should call ClientMembership.assign', function() {
          expect(ClientMembership.assign).toHaveBeenCalledWith(1284, 234, jasmine.any(Function));
        });

        it('should log to the transcript', inject(function(Transcript) {
          expect(Transcript.log).toHaveBeenCalled();
        }));
      });

      it('should not send any request when the client is undefined', function() {
        scope.assignClient();
        expect(ClientMembership.assign).not.toHaveBeenCalled();
      });
    });

    describe('on client unassignment', function() {
      describe('when successful', function() {
        beforeEach(function() {
          ClientMembership.unassign.andCallFake(function(clientId, groupId, success) {
            success();
          });

          scope.unassignClient(app02Client);
        });

        it('should call ClientMembership.unassign', function() {
          expect(ClientMembership.unassign).toHaveBeenCalledWith(1284, 234, jasmine.any(Function));
        });

        it('should log to the transcript', inject(function(Transcript) {
          expect(Transcript.log).toHaveBeenCalled();
        }));
      });

      it('should not send any request when the client is undefined', function() {
        scope.unassignClient();
        expect(ClientMembership.unassign).not.toHaveBeenCalled();
      });
    });

    describe('on secret assignment', function() {
      describe('when successful', function() {
        beforeEach(function() {
          SecretMembership.assign.andCallFake(function(secretId, groupId, success) {
            success();
          });

          scope.selectedSecret = nobodyPgpassSecret.name;
          scope.allowSecret();
        });

        it('should call SecretMembership.assign', function() {
          expect(SecretMembership.assign).toHaveBeenCalledWith(737, 234, jasmine.any(Function));
        });

        it('should log to the transcript', inject(function(Transcript) {
          expect(Transcript.log).toHaveBeenCalled();
        }));
      });

      it('should not send any request when the secret is undefined', function() {
        scope.allowSecret();
        expect(SecretMembership.assign).not.toHaveBeenCalled();
      });
    });

    describe('on secret unassignment', function() {
      describe('when successful', function() {
        beforeEach(function() {
          SecretMembership.unassign.andCallFake(function(secretId, groupId, success) {
            success();
          });

          scope.disallowSecret(nobodyPgpassSecret);
        });

        it('should call SecretMembership.unassign', function() {
          expect(SecretMembership.unassign).toHaveBeenCalledWith(737, 234, jasmine.any(Function));
        });

        it('should log to the transcript', inject(function(Transcript) {
          expect(Transcript.log).toHaveBeenCalled();
        }));
      });

      it('should not send any request when the secret is undefined', function() {
        scope.disallowSecret();
        expect(SecretMembership.unassign).not.toHaveBeenCalled();
      });
    });
  });

  describe('RailsSecretTokenCtrl', function() {
    var scope, ctrl, httpBackend,
      statusAlertListener = jasmine.createSpy('statusAlertListener');

    beforeEach(inject(function($httpBackend, $rootScope, $routeParams, $controller) {
      httpBackend = $httpBackend;

      scope = $rootScope.$new();
      scope.$on('event:status-alert', statusAlertListener);
      scope.close = angular.noop;
      scope.content = 'fakeContent';

      ctrl = $controller('RailsSecretTokenCtrl', {$scope: scope});
    }));

    it('should update the status with the message from the server', function() {
      httpBackend.expectPOST('/admin/secrets/generators/templated', /"name":"group-rails-secret-token"/)
        .respond(400, {success: false, message: 'Cannot create secret.', debugInfo: ''});

      scope.create('group');
      httpBackend.flush();

      expect(statusAlertListener).toHaveBeenCalled();
    });

    describe('when successful', function() {
      beforeEach(function() {
        httpBackend.expectPOST('/admin/secrets/generators/templated', /"name":"group-rails-secret-token"/)
          .respond(200, '');
        scope.create('group');
      });

      it('should send a correct message to the server', function() {
        httpBackend.flush();
      });

      it('should log to the transcript', inject(function(Transcript) {
        httpBackend.flush();
        expect(Transcript.log).toHaveBeenCalled();
      }));
    });
  });

  describe('FileSecretCreateCtrl', function() {
    var scope, ctrl, httpBackend,
      statusAlertListener = jasmine.createSpy('statusAlertListener'),
      createData = {
        name: 'fakeSecret',
        content: 'fakeContent',
        description: '',
        withVersion: false,
        metadata: {}
      };

    beforeEach(inject(function($httpBackend, $rootScope, $routeParams, $controller) {
      httpBackend = $httpBackend;

      scope = $rootScope.$new();
      scope.$on('event:status-alert', statusAlertListener);
      scope.close = angular.noop;
      scope.content = 'fakeContent';

      ctrl = $controller('FileSecretCreateCtrl', {$scope: scope});
    }));

    it('should fail when the metadata is not well formatted', function() {
      scope.create(createData.name, createData.description, 'not a valid JSON string');
      expect(statusAlertListener).toHaveBeenCalled();
    });

    it('should update the status with the message from the server', function() {
      httpBackend.expectPOST('/admin/secrets', createData)
        .respond(400, {success: false, message: 'Cannot create secret.', debugInfo: ''});

      scope.create(createData.name, createData.description, JSON.stringify(createData.metadata));
      httpBackend.flush();

      expect(statusAlertListener).toHaveBeenCalled();
    });

    describe('when successful', function() {
      beforeEach(function() {
        httpBackend.expect('POST', '/admin/secrets', createData).respond(200, '');
        scope.create(createData.name, createData.description, JSON.stringify(createData.metadata));
      });

      it('should send a correct message to the server', function() {
        httpBackend.flush();
      });

      it('should log to the transcript', inject(function(Transcript) {
        httpBackend.flush();
        expect(Transcript.log).toHaveBeenCalled();
      }));
    });
  });

  describe('SecretListCtrl', function() {
    var scope, ctrl, mock$Modal, Secret,
      listingData = function() {
        return [
          { 'id': 0,
            'name': 'database_password',
            'createdAt': '2012-08-01T13:15:30.000Z',
            'createdBy': 'user'
          },
          {
            'id': 1,
            'name': 'online_passphrase',
            'createdAt': '2012-08-01T13:15:30.000Z',
            'createdBy': 'third-user'
          }
        ];
      };

    beforeEach(inject(function($rootScope, $controller) {
      mock$Modal = {open: jasmine.createSpy('open')};

      Secret = jasmine.createSpyObj('Secret', ['query']);
      Secret.query.andCallFake(listingData);

      scope = $rootScope.$new();

      ctrl = $controller('SecretListCtrl', {
        $modal: mock$Modal,
        $scope: scope,
        Secret: Secret
      });
    }));

    it('should create secrets model with 2 entries fetched via xhr', function() {
      expect(scope.secrets).toEqualData(listingData());
    });
  });

  describe('SecretDetailCtrl', function() {
    var scope, ctrl, mock$Modal, Group, Secret, SecretMembership,
      appHostsGroup = {'id': 111, 'name': 'app_hosts'},
      apiHostsGroup = {'id': 112, 'name': 'api_hosts'},
      webGroup = {'id': 113, 'name': 'web'},
      detailData = function() {
        return {
          'name': 'wildcard.crt',
          'id': 33,
          'groups': [ appHostsGroup,
                      apiHostsGroup ],
          'clients': [ {'id': 222, 'name': 'client 1'},
                       {'id': 223, 'name': 'client 2'} ]
        };
      };

    beforeEach(inject(function($rootScope, $routeParams, $controller) {
      mock$Modal = {open: jasmine.createSpy('open')};

      Group = jasmine.createSpyObj('Group', ['query']);
      Group.query.andCallFake(function() {
        return [appHostsGroup, apiHostsGroup, webGroup];
      });

      Secret = jasmine.createSpyObj('Secret', ['get']);
      Secret.get.andCallFake(detailData);

      SecretMembership = jasmine.createSpyObj('SecretMembership', ['assign', 'unassign']);

      $routeParams.secretId = 33;
      scope = $rootScope.$new();

      ctrl = $controller('SecretDetailCtrl', {
        $scope: scope,
        $modal: mock$Modal,
        Group: Group,
        Secret: Secret,
        SecretMembership: SecretMembership
      });
    }));

    it('should create secret model', function() {
      expect(scope.secret).toEqualData(detailData());
      expect(Secret.get).toHaveBeenCalledWith({secretId: 33});
    });

    describe('on group assignment', function() {
      describe('when successful', function() {
        beforeEach(function() {
          SecretMembership.assign.andCallFake(function(secretId, groupId, success) {
            success();
          });

          scope.selected = webGroup.name;
          scope.assignGroup();
        });

        it('should call SecretMembership.assign', function() {
          expect(SecretMembership.assign).toHaveBeenCalledWith(33, 113, jasmine.any(Function));
        });

        it('should log to the transcript', inject(function(Transcript) {
          expect(Transcript.log).toHaveBeenCalled();
        }));
      });

      it('should not call SecretMembership.assign when the group is undefined', function() {
        scope.assignGroup();
        expect(SecretMembership.assign).not.toHaveBeenCalled();
      });
    });

    describe('on group unassignment', function() {
      describe('when successful', function() {
        beforeEach(function() {
          SecretMembership.unassign.andCallFake(function(secretId, groupId, success) {
            success();
          });

          scope.unassignGroup(appHostsGroup);
        });

        it('should call SecretMembership.unassign', function() {
          expect(SecretMembership.unassign).toHaveBeenCalledWith(33, 111, jasmine.any(Function));
        });

        it('should log to the transcript', inject(function(Transcript) {
          expect(Transcript.log).toHaveBeenCalled();
        }));
      });

      it('should not call SecretMembership.unassign when the group is undefined', function() {
        scope.unassignGroup();
        expect(SecretMembership.unassign).not.toHaveBeenCalled();
      });
    });
  });
});
