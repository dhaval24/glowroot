/*
 * Copyright 2014 the original author or authors.
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

/* global glowroot, angular, $, moment */

glowroot.controller('JvmGaugesCtrl', [
  '$scope',
  '$location',
  '$filter',
  '$http',
  '$timeout',
  'keyedColorPools',
  'queryStrings',
  'httpErrors',
  function ($scope, $location, $filter, $http, $timeout, keyedColorPools, queryStrings, httpErrors) {

    var plot;
    var plotGaugeNames;

    var fixedGaugeIntervalMillis = $scope.layout.fixedGaugeIntervalSeconds * 1000;

    var currentRefreshId = 0;
    var currentZoomId = 0;

    var $chart = $('#chart');

    var keyedColorPool = keyedColorPools.create();
    $scope.keyedColorPool = keyedColorPool;

    $scope.$watchCollection('[containerWidth, windowHeight]', function () {
      plot.resize();
      plot.setupGrid();
      plot.draw();
    });

    $scope.showChartSpinner = 0;

    $http.get('backend/jvm/all-gauge-names')
        .success(function (data) {
          $scope.loaded = true;
          $scope.allGaugeNames = data;
          $scope.allShortGaugeNames = createShortGaugeNames($scope.allGaugeNames);

          var gaugeNames = $location.search()['gauge-name'];
          if (angular.isArray(gaugeNames)) {
            angular.forEach(gaugeNames, function (gaugeName) {
              if ($scope.allGaugeNames.indexOf(gaugeName) !== -1) {
                keyedColorPool.add(gaugeName);
              }
            });
          } else if (gaugeNames) {
            if ($scope.allGaugeNames.indexOf(gaugeNames) !== -1) {
              keyedColorPool.add(gaugeNames);
            }
          }
          if (keyedColorPool.keys()) {
            refreshChart();
          }
        })
        .error(httpErrors.handler($scope));

    function createShortGaugeNames(gaugeNames) {
      var splitGaugeNames = [];
      angular.forEach(gaugeNames, function (gaugeName) {
        splitGaugeNames.push(gaugeName.split('/'));
      });
      var minRequiredForUniqueName;
      var shortNames = {};
      for (var i = 0; i < gaugeNames.length; i++) {
        var si = splitGaugeNames[i];
        minRequiredForUniqueName = 1;
        for (var j = 0; j < gaugeNames.length; j++) {
          if (j === i) {
            continue;
          }
          var sj = splitGaugeNames[j];
          for (var k = 1; k <= Math.min(si.length, sj.length); k++) {
            if (si[si.length - k] !== sj[sj.length - k]) {
              break;
            }
            minRequiredForUniqueName = Math.max(minRequiredForUniqueName, k + 1);
          }
        }
        shortNames[gaugeNames[i]] = si.slice(-minRequiredForUniqueName).join('/');
      }
      return shortNames;
    }

    function refreshChart(deferred) {
      var date = $scope.filterDate;
      var refreshId = ++currentRefreshId;
      var chartFrom = $scope.chartFrom;
      var chartTo = $scope.chartTo;
      var query = {
        from: chartFrom - fixedGaugeIntervalMillis,
        to: chartTo + fixedGaugeIntervalMillis,
        gaugeNames: keyedColorPool.keys()
      };
      $scope.showChartSpinner++;
      $http.get('backend/jvm/gauge-points?' + queryStrings.encodeObject(query))
          .success(function (data) {
            $scope.showChartSpinner--;
            if (refreshId !== currentRefreshId) {
              return;
            }
            // reset axis in case user changed the date and then zoomed in/out to trigger this refresh
            plot.getAxes().xaxis.options.min = chartFrom;
            plot.getAxes().xaxis.options.max = chartTo;
            plot.getAxes().xaxis.options.zoomRange = [
              date.getTime(),
                  date.getTime() + 24 * 60 * 60 * 1000
            ];
            var plotData = data;
            plotGaugeNames = [];
            angular.forEach(query.gaugeNames, function (gaugeName) {
              plotGaugeNames.push(gaugeName);
            });
            updatePlotData(plotData);
            if (deferred) {
              deferred.resolve('Success');
            }
          })
          .error(function (data, status) {
            $scope.showChartSpinner--;
            if (refreshId !== currentRefreshId) {
              return;
            }
            httpErrors.handler($scope, deferred)(data, status);
          });
    }

    $scope.refreshButtonClick = function (deferred) {
      var midnight = new Date($scope.chartFrom).setHours(0, 0, 0, 0);
      if (midnight !== $scope.filterDate.getTime()) {
        // filterDate has changed
        chartFromToDefault = false;
        $scope.chartFrom = $scope.filterDate.getTime() + ($scope.chartFrom - midnight);
        $scope.chartTo = $scope.filterDate.getTime() + ($scope.chartTo - midnight);
      }
      refreshChart(deferred);
      updateLocation();
    };

    function updatePlotData(data) {
      var plotData = [];
      var nodata = true;
      // using plotGaugeNames.length since data.length is 1 for dummy data [[]] which is needed for flot to draw
      // gridlines
      if (plotGaugeNames && plotGaugeNames.length) {
        for (var i = 0; i < plotGaugeNames.length; i++) {
          if (nodata) {
            nodata = !data[i].length;
          }
          plotData.push({
            data: data[i],
            color: keyedColorPool.get(plotGaugeNames[i]),
            label: plotGaugeNames[i]
          });
        }
        $scope.chartNoData = nodata;
      } else {
        // don't show "No data" when no gauges were even selected
        $scope.chartNoData = false;
      }
      if (plotData.length) {
        plot.setData(plotData);
      } else {
        plot.setData([
          []
        ]);
      }
      plot.setupGrid();
      plot.draw();
    }

    $chart.bind('plotzoom', function (event, plot, args) {
      $scope.$apply(function () {
        // throw up spinner right away
        $scope.showChartSpinner++;
        $scope.showTableOverlay++;
        var zoomingOut = args.amount && args.amount < 1;
        if (zoomingOut) {
          plot.getAxes().yaxis.options.min = 0;
          plot.getAxes().yaxis.options.realMax = undefined;
        }
        if (zoomingOut) {
          // need to call setupGrid on each zoom to handle rapid zooming
          plot.setupGrid();
          var zoomId = ++currentZoomId;
          // use 100 millisecond delay to handle rapid zooming
          $timeout(function () {
            if (zoomId === currentZoomId) {
              $scope.chartFrom = plot.getAxes().xaxis.min;
              $scope.chartTo = plot.getAxes().xaxis.max;
              chartFromToDefault = false;
              refreshChart();
              updateLocation();
            }
            $scope.showChartSpinner--;
            $scope.showTableOverlay--;
          }, 100);
        } else {
          // no need to fetch new data
          $scope.chartFrom = plot.getAxes().xaxis.min;
          $scope.chartTo = plot.getAxes().xaxis.max;
          chartFromToDefault = false;
          updatePlotData(getFilteredData());
          updateLocation();
          // increment currentRefreshId to cancel any refresh in action
          currentRefreshId++;
          $scope.showChartSpinner--;
          $scope.showTableOverlay--;
        }
      });
    });

    $chart.bind('plotselected', function (event, ranges) {
      $scope.$apply(function () {
        plot.clearSelection();
        // perform the zoom
        plot.getAxes().xaxis.options.min = ranges.xaxis.from;
        plot.getAxes().xaxis.options.max = ranges.xaxis.to;
        updatePlotData(getFilteredData());
        $scope.chartFrom = plot.getAxes().xaxis.min;
        $scope.chartTo = plot.getAxes().xaxis.max;
        chartFromToDefault = false;
        updateLocation();
        // no need to fetch new data
        // increment currentRefreshId to cancel any refresh in action
        currentRefreshId++;
      });
    });

    function getFilteredData() {
      var from = plot.getAxes().xaxis.options.min;
      var to = plot.getAxes().xaxis.options.max;
      var i, j;
      var filteredData = [];
      var data = plot.getData();
      for (i = 0; i < data.length; i++) {
        var filteredPoints = [];
        var points = data[i].data;
        var lastPoint = null;
        var lastPointInRange = false;
        for (j = 0; j < points.length; j++) {
          var currPoint = points[j];
          if (!currPoint) {
            // point can be undefined which is used to represent no data collected in that period
            if (lastPointInRange) {
              filteredPoints.push(currPoint);
            }
            lastPoint = currPoint;
            continue;
          }
          var currPointInRange = currPoint[0] >= from && currPoint[0] <= to;
          if (!lastPointInRange && currPointInRange) {
            // add one extra so partial line slope to point off the chart will be displayed
            filteredPoints.push(lastPoint);
            filteredPoints.push(currPoint);
          } else if (lastPointInRange && !currPointInRange) {
            // add one extra so partial line slope to point off the chart will be displayed
            filteredPoints.push(currPoint);
            // past the end, no other data points need to be checked
            break;
          }
          if (currPointInRange) {
            filteredPoints.push(currPoint);
          }
          lastPoint = currPoint;
          lastPointInRange = currPointInRange;
        }
        filteredData.push(filteredPoints);
      }
      return filteredData;
    }

    $scope.gaugeRowStyle = function (gaugeName) {
      var color = keyedColorPool.get(gaugeName);
      if (color) {
        return {
          color: color,
          'font-weight': 'bold'
        };
      } else {
        return {
          'font-weight': 'normal'
        };
      }
    };

    $scope.displayFilterRowStyle = function (gaugeName) {
      var color = keyedColorPool.get(gaugeName);
      return {
        'background-color': color,
        color: 'white',
        padding: '5px 10px',
        // for some reason there is already a gap when running under grunt serve, and margin-right makes it too big
        // but this is correct when not running under grunt serve
        'margin-right': '5px',
        'border-radius': '3px',
        'margin-bottom': '5px'
      };
    };

    $scope.clickGaugeName = function (gaugeName) {
      var color = keyedColorPool.get(gaugeName);
      if (color) {
        // uncheck it
        keyedColorPool.remove(gaugeName);
      } else {
        // check it
        color = keyedColorPool.add(gaugeName);
      }
      refreshChart();
      updateLocation();
    };

    $scope.removeDisplayedGauge = function (gaugeName) {
      keyedColorPool.remove(gaugeName);
      refreshChart();
      updateLocation();
    };

    $scope.selectAllGauges = function () {
      var gaugeNames = $filter('filter')($scope.allGaugeNames, $scope.gaugeNameFilter);
      angular.forEach(gaugeNames, function (gaugeName) {
        keyedColorPool.add(gaugeName);
      });
      refreshChart();
      updateLocation();
    };

    $scope.deselectAllGauges = function () {
      var gaugeNames = $filter('filter')($scope.allGaugeNames, $scope.gaugeNameFilter);
      angular.forEach(gaugeNames, function (gaugeName) {
        keyedColorPool.remove(gaugeName);
      });
      refreshChart();
      updateLocation();
    };

    $scope.zoomOut = function () {
      // need to execute this outside of $apply since code assumes it needs to do its own $apply
      $timeout(function () {
        plot.zoomOut();
      });
    };

    var chartFromToDefault;

    $scope.filter = {};
    $scope.chartFrom = Number($location.search().from);
    $scope.chartTo = Number($location.search().to);
    // both from and to must be supplied or neither will take effect
    if ($scope.chartFrom && $scope.chartTo) {
      $scope.filterDate = new Date($scope.chartFrom);
      $scope.filterDate.setHours(0, 0, 0, 0);
    } else {
      chartFromToDefault = true;
      var today = new Date();
      today.setHours(0, 0, 0, 0);
      $scope.filterDate = today;
      // show 2 hour interval, but nothing prior to today (e.g. if 'now' is 1am) or after today
      // (e.g. if 'now' is 11:55pm)
      var now = new Date();
      now.setSeconds(0, 0);
      $scope.chartFrom = Math.max(now.getTime() - 105 * 60 * 1000, today.getTime());
      $scope.chartTo = Math.min($scope.chartFrom + 120 * 60 * 1000, today.getTime() + 24 * 60 * 60 * 1000);
    }

    function updateLocation() {
      var query = {};
      if (!chartFromToDefault) {
        query.from = $scope.chartFrom;
        query.to = $scope.chartTo;
      }
      query['gauge-name'] = keyedColorPool.keys();
      $location.search(query).replace();
    }

    (function () {
      var options = {
        grid: {
          mouseActiveRadius: 10,
          // without specifying min border margin, the point radius is used
          minBorderMargin: 0,
          borderColor: '#7d7358',
          borderWidth: 1,
          // this is needed for tooltip plugin to work
          hoverable: true
        },
        legend: {
          show: false
        },
        xaxis: {
          mode: 'time',
          timezone: 'browser',
          twelveHourClock: true,
          ticks: 5,
          min: $scope.chartFrom,
          max: $scope.chartTo,
          absoluteZoomRange: true,
          zoomRange: [
            $scope.filterDate.getTime(),
                $scope.filterDate.getTime() + 24 * 60 * 60 * 1000
          ],
          reserveSpace: false
        },
        yaxis: {
          ticks: 10,
          zoomRange: false,
          min: 0,
          // 10 second yaxis max just for initial empty chart rendering
          max: 10
        },
        zoom: {
          interactive: true,
          amount: 2,
          skipDraw: true
        },
        selection: {
          mode: 'x'
        },
        series: {
          lines: {
            show: true
          },
          points: {
            radius: 6
          }
        },
        tooltip: true,
        tooltipOpts: {
          content: function (label, xval, yval, flotItem) {
            // TODO internationalize time format
            var shortLabel = $scope.allShortGaugeNames[label];
            return moment(xval).format('h:mm:ss.SSS a (Z)') + '<br>' + shortLabel + ': ' + yval;
          }
        }
      };
      // render chart with no data points
      plot = $.plot($chart, [
        []
      ], options);
      plot.getAxes().xaxis.options.borderGridLock = 1;
    })();

    plot.getAxes().yaxis.options.max = undefined;
  }
]);
