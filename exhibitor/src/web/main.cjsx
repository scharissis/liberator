React = require 'react'
Router = require 'react-router'
Link = Router.Link
RouteHandler = Router.RouteHandler

module.exports = React.createClass
  render: ->
    <div>
      <header><Link to="home">Home</Link></header>
      <RouteHandler/>
    </div>
