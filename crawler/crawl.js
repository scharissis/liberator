var GitHubApi = require("github");

var github = new GitHubApi({
  version: '3.0.0',
  debug: true
});


github.search.repos({
  q: 'language:javascript',
  sort: 'stars',
  per_page: 100
}, function(err, res) {
  console.log(JSON.stringify(res, null, 2));
});
