var path = require('path');
var HtmlWebpackPlugin = require('html-webpack-plugin');

var DIST_DIR = 'dist';

module.exports = {
  entry: {
    app: path.join(__dirname, 'src/web/app.cjsx')
  },
  output: {
    path: path.join(__dirname, DIST_DIR),
    filename: '[name]_bundle_[hash].js'
  },
  resolve: {
    extensions: ['', '.js', '.cjsx', '.coffee']
  },
  module: {
    loaders: [
      { test: /\.cjsx$/, loaders: ['coffee', 'cjsx']},
      { test: /\.coffee$/, loader: 'coffee' }
    ],
  },
  plugins: [
    new HtmlWebpackPlugin()
  ]
};
