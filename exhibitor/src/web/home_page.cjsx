React = require 'react'
Alert = require('react-bootstrap').Alert

c3 = require 'web/vendor/c3'

module.exports =  React.createClass
  componentDidMount: ->
    c3.generate
      bindto: '#chart',
      data:
        columns: [
          ['facebook/react', 30, 200, 100, 400, 150, 250],
          ['gruntjs/grunt', 50, 20, 10, 40, 15, 25]
        ]

  render: ->
    <div>
      <h1>Liberator</h1>
      <Alert bsStyle="warning">Search Results</Alert>
      <div id="chart"></div>
    </div>
