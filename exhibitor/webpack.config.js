var path = require('path');
var webpack = require('webpack');
var HtmlWebpackPlugin = require('html-webpack-plugin');

var DIST_DIR = 'dist';

module.exports = {
  entry: [
    'webpack-dev-server/client?http://localhost:3001',
    'webpack/hot/dev-server',
    path.join(__dirname, 'src/web/index.cjsx')
  ],
  output: {
    path: path.join(__dirname, DIST_DIR),
    filename: 'bundle_[hash].js'
  },
  resolve: {
    modulesDirectories: ['src', 'node_modules'],
    extensions: ['', '.js', '.cjsx', '.coffee']
  },
  module: {
    loaders: [
      { test: /\.cjsx$/, loaders: ['react-hot', 'coffee', 'cjsx']},
      { test: /\.coffee$/, loader: 'coffee' },
      { test: /\.css$/, loaders: ['style', 'css']},
      { test: /\.svg$/, loader: 'url-loader?prefix=images/&limit=10000&mimetype=image/svg+xml' },
      { test: /\.woff$/, loader: 'url-loader?prefix=fonts/&limit=10000&mimetype=application/font-woff' },
      { test: /\.eot$/, loader: 'url-loader?prefix=fonts/&limit=10000&mimetype=application/vnd.ms-fontobject' },
      { test: /\.ttf$/, loader: 'url-loader?prefix=fonts/&limit=10000&mimetype=application/octet-stream' }
    ],
  },
  plugins: [
    new HtmlWebpackPlugin(),
    new webpack.HotModuleReplacementPlugin(),
    new webpack.NoErrorsPlugin()
  ],
  devtool: 'source-map',
  cache: true
};
