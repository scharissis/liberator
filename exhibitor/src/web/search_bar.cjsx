React = require 'react'
ReactBootstrap = require 'react-bootstrap'
Input = ReactBootstrap.Input

LibraryActions = require 'web/library_actions'

module.exports = React.createClass
  handleSubmit: (e) ->
    e.preventDefault()
    search_string = this.refs.search.getValue().trim()
    if search_string
      LibraryActions.search(search_string)

  render: ->
    <form onSubmit={this.handleSubmit}>
      <Input
        type="text"
        placeholder="npm/react, npm/angular"
        label="Node (NPM) libraries, comma separated"
        ref="search" />
    </form>
