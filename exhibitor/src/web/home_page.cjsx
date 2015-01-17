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
    <div id="home-page" className="container">
      <div className="page-header">
        <h1>Liberator <small>Language package usage</small></h1>
      </div>
      <SearchBar></SearchBar>
      <UsageGraph libraries={this.state.libraries}></UsageGraph>
    </div>
