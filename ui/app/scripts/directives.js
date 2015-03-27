'use strict';

/* Directives */

angular.module('keywhizUi.directives', [])
  // Creates show/hide actions on login/main elements based on auth-login events.
  .directive('authKeywhizUi', function() {
    return {
      restrict: 'C', // Only matches class values.
      link: function(scope, elem) {
        scope.$on('event:auth-loginRequired', function() {
          elem.find('.loggedin-only').hide();
          elem.find('.loggedout-only').show();
        });
        scope.$on('event:auth-loginConfirmed', function() {
          elem.find('.loggedin-only').show();
          elem.find('.loggedout-only').hide();
        });
      }
    };
  });
