'use strict';

/* Services */
angular.module('keywhizUi.services', ['ngResource'])
  .value('transcriptMaxLength', 1000)
  .value('undoTimeoutMillis', 10000)

  .factory('Client', function($resource) {
    return $resource('/admin/clients/:clientId', {clientId:'@id'});
  })

  .factory('Group', function($resource) {
    return $resource('/admin/groups/:groupId', {groupId:'@id'});
  })

  .factory('Secret', function($resource) {
    return $resource('/admin/secrets/:secretId', {secretId:'@id'});
  })

  .service('ClientMembership', function($resource) {
    var resource = $resource('/admin/memberships/clients/:clientId/groups/:groupId', {}, {
      assign: {method: 'PUT'},
      unassign: {method: 'DELETE'}
    });

    /*
     * Assign a client to a group. Calls success callback on success, fail callback otherwise.
     */
    this.assign = function(clientId, groupId, success, fail) {
      return resource.assign({
          clientId: clientId,
          groupId: groupId
        },
        {}, // Unused POST params
        success,
        fail
      );
    };

    /*
     * Un-assign a client to a group. Calls success callback on success, fail callback otherwise.
     */
    this.unassign = function(clientId, groupId, success, fail) {
      return resource.unassign({
          clientId: clientId,
          groupId: groupId
        },
        {}, // Unused POST params
        success,
        fail
      );
    };
  })

  .service('SecretMembership', function($resource) {
    var resource = $resource('/admin/memberships/secrets/:secretId/groups/:groupId', {}, {
      assign: {method: 'PUT'},
      unassign: {method: 'DELETE'}
    });

    /*
     * Assign a secret to a group. Calls success callback on success, fail callback otherwise.
     */
    this.assign = function(secretId, groupId, success, fail) {
      return resource.assign({
          secretId: secretId,
          groupId: groupId
        },
        {}, // Unused POST params
        success,
        fail
      );
    };

    /*
     * Assign a secret to a group. Calls success callback on success, fail callback otherwise.
     */
    this.unassign = function(secretId, groupId, success, fail) {
      return resource.unassign({
          secretId: secretId,
          groupId: groupId
        },
        {}, // Unused POST params
        success,
        fail
      );
    };
  })

  // This service implements a high-level undo functionality that
  // can be used by any controller. It sets an undoMessage inside
  // the scope of the invoking controller to show/hide the undo
  // message. The undo is canceled after undoTimeoutMillis milliseconds.
  .service('Undo', function(undoTimeoutMillis, $timeout) {
    var undoAction = null;
    var undoLock = false;

    // Saves the closure that will be called when invoking undo()
    // Input:
    // closure is the function that is called upon undo
    // message is the undoMessage that is set inside scope.undoMessage
    // of the invoking controller
    // Output:
    // None
    this.updateUndo = function (closure, message, scope) {
      // We don't want to have a self-triggering chain of undos
      // This works only when the undoing closure are mutually
      // updating the undo
      if (undoLock) {
        undoLock = false;
        return;
      }

      scope.undoMessage = message;
      undoAction = function() {
        closure();
        scope.undoMessage = '';
      };

      scope.close = function() {
        undoAction = null;
        scope.undoMessage = '';
      };

      // The undo action is only valid for a limited time
      $timeout.cancel(this.timeout);
      this.timeout = $timeout(scope.close, undoTimeoutMillis);
    };

    this.undo = function () {
      if (undoAction === null) { return; }
      undoLock = true;
      undoAction();
      undoAction = null;
    };
  })

  // Provides an ordered series of events (e.g. Transcript.log('a', 'b', 'c') === ['a', 'b', 'c']).
  .service('Transcript', function(transcriptMaxLength) {
    var log = [];

    // Clears the transcript of all events.
    this.clear = function() {
      log = [];
    };

    // Pushes zero or more events onto the transcript.
    // Returns an array copy of the entire event log.
    // If the new number of events exceeds ```maximumSize``` then the oldest events are dropped.
    this.log = function() {
      var args = Array.prototype.slice.call(arguments);

      if (args.length > 0) {
        // log is a fixed-length queue: push on the end, splice from the front
        var length = Array.prototype.push.apply(log, args);
        if (length > transcriptMaxLength) {
          log.splice(0, length - transcriptMaxLength);
        }
      }

      return angular.copy(log);
    };

    // Maximum constant size of Transcript.
    this.maximumSize = transcriptMaxLength;
  })

  .service('Timestamp', function($filter) {
    this.short = function() {
      return $filter('date')(new Date(), 'short');
    };
  })

  /*
   * Session factory mediates login, logout, and current session status.
   */
  .service('Session', function($http, $rootScope, $q, authService) {
    /*
     * Check if currently logged in.
     *
     * If so, signal the authService and return username.
     * If not, broadcast login failure event.
     */
    this.current = function() {
      var deferred = $q.defer();

      $http.get('/admin/me', null, {doNotIntercept:true})
        .success(function(data) {
          authService.loginConfirmed();
          deferred.resolve(data ? data.name : null);
        })
        .error(function() {
          $rootScope.$broadcast('event:auth-loginRequired');
          deferred.reject();
        });

      return deferred.promise;
    };

    /*
     * Attempt to login given credentials.
     *
     * If successful, signal the authService.
     * If not, return an error message.
     */
    this.login = function(credentials) {
      var deferred = $q.defer();

      $http.post('/admin/login', credentials, {doNotIntercept:true})
        .success(function() {
          authService.loginConfirmed();
          deferred.resolve();
        })
        .error(function(data) {
          if (data && data.message) {
            deferred.reject(data.message);
          } else {
            deferred.reject('Invalid login.');
          }
        });

      return deferred.promise;
    };

    /*
     * Attempt to logout. Broadcast loginRequired if successful.
     */
    this.logout = function() {
      $http.post('/admin/logout', null, {doNotIntercept:true})
        .success(function() {
          $rootScope.$broadcast('event:auth-loginRequired');
        });
    };
  })

  .service('ErrorMessage', function() {
    this.extract = function(data) {
      if (data && data.data && data.data.message) {
        return data.data.message;
      } else {
        return 'Server returned an unknown error.';
      }
    };
  });
