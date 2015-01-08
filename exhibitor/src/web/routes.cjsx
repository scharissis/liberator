React = require 'react'
Router = require 'react-router'
Route = Router.Route

Main = require 'web/main'
HomePage = require 'web/home_page'

module.exports = (
  <Route handler={Main}>
    <Route name="home" handler={HomePage} path="/" />
  </Route>
)
