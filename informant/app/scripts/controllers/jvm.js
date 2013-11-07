/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* global informant */

informant.controller('JvmCtrl', [
  '$scope',
  '$state',
  function ($scope, $state) {
    // \u00b7 is &middot;
    document.title = 'JVM \u00b7 Informant';
    $scope.$parent.title = 'JVM';
    $scope.$parent.activeNavbarItem = 'jvm';

    $scope.isCurrentView = function (viewName) {
      return $state.current.name === viewName;
    };
  }
]);
