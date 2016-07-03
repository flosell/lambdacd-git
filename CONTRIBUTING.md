# Contribution Guide

## Contributions encouraged

I'd love to hear from you! If you have a question, bug report, feature request or a pull request, please reach out.

## How to reach out

The preferred way at the moment is to open issues on the [Github Issue Tracker](https://github.com/flosell/lambdacd/issues)

If you want to contribute improvements to the codebase, open a pull request.

## How to open the perfect issue

* Be specific and as detailed as you feel is necessary to understand the topic
* Provide context (what were you trying to achive, what were you expecting, ...)
* Code samples and logs can be really helpful. Consider [Gists](https://gist.github.com/) or links to other Github repos
  for larger pieces.
* If you are reporting a bug, add steps to reproduce it.

## How to create the perfect pull request

* Have a look into the [`README`](README.md#development) for details on how to work with the
  code
* Follow the usual best practices for pull requests:
  * use a branch
  * make sure you have pulled changes from upstream so that your change is easy to merge
  * follow the conventions in the code
  * keep a tidy commit history that speaks for itself, consider squashing commits where appropriate
* Run all the tests: `./go test`
* Add tests where possible
* Add an entry in [`CHANGELOG.md`](CHANGELOG.md) if you add new features, fix bugs or otherwise change LambdaCD in a way that you want
  users to be aware of. The entry goes into the section for the next release (which is the version number indicated in
  `project.clj`), usually the top one. If that section doesn't exist yet, add it.
