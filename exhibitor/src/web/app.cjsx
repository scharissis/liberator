React = require 'react'
Router = require 'react-router'
Route = Router.Route
Link = Router.Link
RouteHandler = Router.RouteHandler

# React dev tools looks for this
window.React = React

App = React.createClass
  render: ->
    <div>
      <header><Link to="hello">Hello</Link></header>
      <RouteHandler/>
    </div>

HelloWorld =  React.createClass
  render: ->
    <div>
      <h1>Hello world</h1>
    </div>

routes = (
  <Route handler={App}>
    <Route name="hello" handler={HelloWorld} path="/" />
  </Route>
)

Router.run routes, Router.HistoryLocation, (Handler) ->
  React.render <Handler/>, document.body
