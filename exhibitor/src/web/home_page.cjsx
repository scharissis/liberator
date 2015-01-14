co = require 'co'
React = require 'react'
Alert = require('react-bootstrap').Alert

c3 = require 'web/vendor/c3'
api = require 'web/api'

module.exports =  React.createClass
  componentDidMount: ->
    co ->
      data = yield api.libraries()
      c3.generate
        bindto: '#chart',
        data: data

  render: ->
    <div>
      <h1>Liberator</h1>
      <Alert bsStyle="warning">Search Results</Alert>
      <div id="chart"></div>
    </div>
