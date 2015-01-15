_ = require 'lodash'
co = require 'co'
React = require 'react'
Alert = require('react-bootstrap').Alert

c3 = require 'web/vendor/c3'
api = require 'web/api'

module.exports =  React.createClass
  componentDidMount: ->
    co ->
      libraries = yield api.libraries()
      columns = []
      columns.push ['dates'].concat(_.first(libraries).usage.dates)
      columns = columns.concat libraries.map (lib) ->
        [lib.id].concat(lib.usage.values)

      c3.generate
        bindto: '#chart'
        data:
          x: 'dates'
          columns: columns
        axis:
          x:
            type: 'timeseries',
            tick:
              format: '%Y-%m-%d'

  render: ->
    <div>
      <h1>Liberator</h1>
      <Alert bsStyle="warning">Search Results</Alert>
      <div id="chart"></div>
    </div>
