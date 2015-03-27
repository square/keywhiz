'use strict';

// Declare app level module which depends on filters, and services
angular.module('keywhizUi', [
    'ngRoute',
    'keywhizUi.controllers',
    'keywhizUi.filters',
    'keywhizUi.services',
    'keywhizUi.directives',
    'http-auth-interceptor',
    'ui.bootstrap'
  ])
  .config(['$routeProvider', function($routeProvider) {
    $routeProvider
      .when('/clients', {templateUrl: 'partials/clientList.html', controller: 'ClientListCtrl'})
      .when('/clients/:clientId', {templateUrl: 'partials/clientDetail.html', controller: 'ClientDetailCtrl'})
      .when('/groups', {templateUrl: 'partials/groupList.html', controller: 'GroupListCtrl'})
      .when('/groups/:groupId', {templateUrl: 'partials/groupDetail.html', controller: 'GroupDetailCtrl'})
      .when('/secrets', {templateUrl: 'partials/secretList.html', controller: 'SecretListCtrl'})
      .when('/secrets/:secretId', {templateUrl: 'partials/secretDetail.html', controller: 'SecretDetailCtrl'})
      .when('/transcript', {templateUrl: 'partials/transcript.html', controller: 'TranscriptCtrl'})
      .otherwise({redirectTo: '/secrets'});
  }]);

/*
TODO(justin):
- rollup versions of secrets into one entry
 */
