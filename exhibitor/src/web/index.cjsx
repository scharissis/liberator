require 'regenerator/runtime'
React = require 'react'
Router = require 'react-router'

# React dev tools looks for this
window.React = React

Routes = require 'web/routes'

Router.run Routes, Router.HistoryLocation, (Handler) ->
  React.render <Handler/>, document.body
