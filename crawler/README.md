You'll want to generate a Github API token. Go to your github preferences
and generate a "Personal Access Token". This token only needs to be able
to access public repos. Set the token into a GITHUB_API_TOKEN environment
variable.

You need latest master of coffescript installed (for yield support):

```bash
$ npm install -g jashkenas/coffeescript
```

To run the crawler:

```bash
$ bin/crawl
```

Repos to crawl are presently hard coded in cli.coffee.
