async = require 'async'
co = require 'co'
log = require './log'
RepoCrawlRequest = require './repo_crawl_request'
GithubCrawler = require './github_crawler'
LocalFileSystem = require './local_file_system'

# Configurable params - will be externalised at some point
output_dir = './tmp'
max_concurrent_crawls = 1
repos = [
  "adam-p/markdown-here",
  "addyosmani/backbone-fundamentals",
  "adobe/brackets",
  "ajaxorg/ace",
  "alvarotrigo/fullPage.js",
  "angular/angular.js",
  "angular/angular-seed",
  "angular-ui/bootstrap",
  "Automattic/socket.io",
  "balderdashy/sails",
  "bartaz/impress.js",
  "blueimp/jQuery-File-Upload",
  "bower/bower",
  "browserstate/history.js",
  "caolan/async",
  "carhartl/jquery-cookie",
  "ccampbell/mousetrap",
  "codemirror/CodeMirror",
  "defunkt/jquery-pjax",
  "desandro/masonry",
  "designmodo/Flat-UI",
  "DmitryBaranovskiy/raphael",
  "driftyco/ionic",
  "emberjs/ember.js",
  "facebook/react",
  "enyo/dropzone",
  "etsy/statsd",
  "Famous/famous",
  "fgnass/spin.js",
  "flightjs/flight",
  "FredrikNoren/ungit",
  "ftlabs/fastclick",
  "getify/You-Dont-Know-JS",
  "GitbookIO/gitbook",
  "GoodBoyDigital/pixi.js",
  "gruntjs/grunt",
  "guillaumepotier/Parsley.js",
  "gulpjs/gulp",
  "h5bp/html5-boilerplate",
  "hakimel/reveal.js",
  "hammerjs/hammer.js",
  "ivaynberg/select2",
  "jadejs/jade",
  "janl/mustache.js",
  "jashkenas/backbone",
  "jashkenas/underscore",
  "joyent/node",
  "jquery/jquery",
  "jquery/jquery-mobile",
  "jquery/jquery-ui",
  "julianshapiro/velocity",
  "kamens/jQuery-menu-aim",
  "kenwheeler/slick",
  "knockout/knockout",
  "kriskowal/q",
  "Leaflet/Leaflet",
  "LearnBoost/mongoose",
  "less/less.js",
  "linnovate/mean",
  "lodash/lodash",
  "madrobby/zepto",
  "marionettejs/backbone.marionette",
  "marmelab/gremlins.js",
  "mbostock/d3",
  "meteor/meteor",
  "mochajs/mocha",
  "Modernizr/Modernizr",
  "moment/moment",
  "mozilla/pdf.js",
  "mrdoob/three.js",
  "nnnick/Chart.js",
  "node-inspector/node-inspector",
  "NUKnightLab/TimelineJS",
  "peachananr/onepage-scroll",
  "photonstorm/phaser",
  "pivotal/jasmine",
  "Polymer/polymer",
  "Prinzhorn/skrollr",
  "request/request",
  "resume/resume.github.com",
  "rstacruz/jquery.transit",
  "rstacruz/nprogress",
  "rwaldron/idiomatic.js",
  "scottjehl/picturefill",
  "scottjehl/Respond",
  "Semantic-Org/Semantic-UI",
  "shichuan/javascript-patterns",
  "Shopify/dashing",
  "sindresorhus/pageres",
  "strongloop/express",
  "substack/node-browserify",
  "tastejs/todomvc",
  "TryGhost/Ghost",
  "twitter/typeahead.js",
  "usablica/intro.js",
  "videojs/video.js",
  "wagerfield/parallax",
  "WickyNilliams/headroom.js",
  "wycats/handlebars.js",
  "xing/wysihtml5"
]



crawl_repo = (repo_crawl_request, callback) ->
  co ->
    yield crawler.crawl repo_crawl_request
    callback()

output_file_system = new LocalFileSystem(output_dir)
crawler = new GithubCrawler(output_file_system)
crawl_requests = repos.map (repo) -> new RepoCrawlRequest 'github', repo
crawl_queue = async.queue(crawl_repo, max_concurrent_crawls)
crawl_queue.push(crawl_requests)

crawl_queue.drain = -> log.info('Crawl complete')
