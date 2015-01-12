React = require 'react'
Router = require 'react-router'
Route = Router.Route

App = require 'web/app'
HomePage = require 'web/home_page'

module.exports = (
  <Route handler={App}>
    <Route name="home" handler={HomePage} path="/" />
  </Route>
)
