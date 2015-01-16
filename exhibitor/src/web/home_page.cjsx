_ = require 'lodash'
React = require 'react'
Reflux = require 'reflux'

SearchBar = require 'web/search_bar'
UsageGraph = require 'web/usage_graph'
LibraryStore = require 'web/library_store'

module.exports =  React.createClass
  mixins: [Reflux.connect(LibraryStore, "libraries")]

  getInitialState: ->
    {
      libraries: []
    }

  render: ->
    <div id="home-page">
      <h1>Liberator</h1>
      <SearchBar></SearchBar>
      <UsageGraph libraries={this.state.libraries}></UsageGraph>
    </div>
