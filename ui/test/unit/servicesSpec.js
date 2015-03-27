'use strict';

/* jasmine specs for services go here */
describe('service', function() {
  beforeEach(module('keywhizUi.services'));

  describe('transcriptMaxLength', function() {
    it('should be positive', inject(function(transcriptMaxLength) {
      expect(transcriptMaxLength).toBeGreaterThan(0);
    }));
  });

  describe('undoTimeout', function() {
    it('should be positive', inject(function(undoTimeoutMillis) {
      expect(undoTimeoutMillis).toBeGreaterThan(0);
    }));
  });

  describe('Undo', function() {
    it('should set the undo message', inject(function(Undo) {
      var mockScope = {};
      var mockClosure = function () { };
      Undo.updateUndo(mockClosure, 'Undo Message', mockScope);
      expect(mockScope.undoMessage).toEqual('Undo Message');
    }));

    it('should not undo the same action more than once', inject(function(Undo) {
      var mockScope = {};
      var callCounter = 0;
      var mockClosure = function () { callCounter = callCounter + 1;};

      Undo.updateUndo(mockClosure, 'Undo Message', mockScope);
      Undo.undo(mockScope);
      Undo.undo(mockScope);
      expect(callCounter).toEqual(1);
    }));

    it('should not update undo when performing an undo action', inject(function(Undo) {
      var mockScope = {};
      var mockClosure = function () { };

      Undo.updateUndo(mockClosure, 'First Undo Message', mockScope);
      Undo.undo(mockScope);
      Undo.updateUndo(mockClosure, 'Second Undo Message', mockScope);
      expect(mockScope.undoMessage).toEqual('');
    }));

    it('should make the undo message disappear on timeout', inject(function(Undo, undoTimeoutMillis, $timeout) {
      var mockScope = {$apply: function() {}};

      undoTimeoutMillis = 1;

      Undo.updateUndo(null, null, mockScope);
      $timeout.flush();
      expect(mockScope.undoMessage).toEqual('');
    }));
  });

  describe('Transcript', function() {
    it('should append to the log', inject(function(Transcript) {
      expect(Transcript.log('a')).toEqual(['a']);
      expect(Transcript.log('b', 'c')).toEqual(['a', 'b', 'c']);
    }));

    it('should allow clearing', inject(function(Transcript) {
      Transcript.log('a', 'b');
      expect(Transcript.log()).toEqual(['a', 'b']);

      Transcript.clear();
      expect(Transcript.log()).toEqual([]);
    }));

    it('should have limited size', inject(function(Transcript) {
      for (var i = 0; i < Transcript.maximumSize; i++) {
        Transcript.log(i);
      }
      expect(Transcript.log().length).toEqual(Transcript.maximumSize);

      Transcript.log(Transcript.maximumSize);
      expect(Transcript.log().length).toEqual(Transcript.maximumSize);
    }));

    it('should drop oldest events when full', inject(function(Transcript) {
      for (var i = 0; i < Transcript.maximumSize; i++) {
        Transcript.log(i);
      }
      expect(Transcript.log()[0]).toEqual(0);

      Transcript.log(Transcript.maximumSize);
      expect(Transcript.log()[0]).toEqual(1);
    }));
  });

  describe('Session', function() {
    var Session, $httpBackend, scope, mockAuthService,
      loginRequiredListener = jasmine.createSpy('loginRequired'),
      credentials = {
        username: 'user1',
        password: 'pass1'
      };

    beforeEach(function() {
      mockAuthService = { loginConfirmed: jasmine.createSpy('loginConfirmed') };

      module(function($provide) {
        $provide.value('authService', mockAuthService);
      });

      inject(function($injector) {
        $httpBackend = $injector.get('$httpBackend');
        Session = $injector.get('Session');

        scope = $injector.get('$rootScope').$new();
        scope.$on('event:auth-loginRequired', loginRequiredListener);
      });
    });

    afterEach(function() {
      $httpBackend.verifyNoOutstandingExpectation();
      $httpBackend.verifyNoOutstandingRequest();
    });

    describe('#current', function() {
      it('should send a GET request', function() {
        $httpBackend.expectGET('/admin/me').respond();
        Session.current();
        $httpBackend.flush();
      });

      describe('on success', function() {
        var username = 'keywhizAdmin';

        beforeEach(function() {
          $httpBackend.expectGET('/admin/me').respond({name: username});
        });

        it('should confirm logged in to authService', function() {
          Session.current();
          $httpBackend.flush();
          expect(mockAuthService.loginConfirmed).toHaveBeenCalled();
        });

        it('should return the name from response', function() {
          var promise = Session.current();
          $httpBackend.flush();
          promise.then(function(response) {
            expect(response).toEqual(username);
          });
        });
      });

      describe('on error', function() {
        it('should broadcast "loginRequired" event', function() {
          $httpBackend.expectGET('/admin/me').respond(401);
          Session.current();
          $httpBackend.flush();
          expect(loginRequiredListener).toHaveBeenCalled();
        });
      });
    });

    describe('#login', function() {
      it('should send POST request', function() {
        $httpBackend.expectPOST('/admin/login', credentials).respond();
        Session.login(credentials);
        $httpBackend.flush();
      });

      describe('on success', function() {
        beforeEach(function() {
          $httpBackend.expectPOST('/admin/login', credentials).respond(201);
        });

        it('should confirm logged in to authService', function() {
          Session.login(credentials);
          $httpBackend.flush();
          expect(mockAuthService.loginConfirmed).toHaveBeenCalled();
        });
      });

      describe('on error', function() {
        beforeEach(function() {
          $httpBackend.expectPOST('/admin/login', credentials).respond(401);
        });

        it('should return error message', function () {
          var errorMessage = null;
          var promise = Session.login(credentials, errorMessage);
          $httpBackend.flush();
          promise.then(function(errorMessage) {
            expect(errorMessage.length).toBeGreaterThan(0);
          });
        });
      });
    });

    describe('#logout', function() {
      it('should send POST request', function() {
        $httpBackend.expectPOST('/admin/logout').respond();
        Session.logout();
        $httpBackend.flush();
      });

      describe('on success', function() {
        beforeEach(function() {
          $httpBackend.expectPOST('/admin/logout').respond(201);
          Session.logout();
        });

        it('should broadcast "loginRequired" event', function() {
          $httpBackend.flush();
          expect(loginRequiredListener).toHaveBeenCalled();
        });
      });
    });
  });
});
