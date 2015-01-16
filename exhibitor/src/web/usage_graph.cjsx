co = require 'co'
React = require 'react'

c3 = require 'web/vendor/c3'
api = require 'web/api'

module.exports =  React.createClass
  propTypes:
    libraries: React.PropTypes.array

  getInitialState: ->
    {
      graph_displayed: false
    }

  getDefaultProps: ->
    {
      libraries: []
    }

  componentDidMount: ->
    this.renderGraph()

  componentDidUpdate: ->
    this.renderGraph()

  renderGraph: ->
    libraries = this.props.libraries
    if libraries.length > 0
      columns = []
      columns.push ['dates'].concat(_.first(libraries).stats.dates)
      columns = columns.concat libraries.map (lib) ->
        [lib.id].concat(lib.stats.usage)

      c3.generate
        bindto: '#home-page .usage-graph'
        data:
          x: 'dates'
          columns: columns
        axis:
          x:
            type: 'timeseries',
            tick:
              format: '%Y-%m-%d'
        point:
          show: false

  render: ->
    <div className="usage-graph">
    </div>
