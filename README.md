# Getting Started

Hello Async TI Developers!

This repository is for the AsyncTI4 Game Management Bot which is written in Java using the Discord JDA library to interface with Discord. The AsyncTI4 Game Management Bot is what handles the slash commands, buttons, map images, server management automation, etc.

To get started with development, please read the following:

1. [Code of Conduct](CODE_OF_CONDUCT.md)
2. [Contribution Guide](CONTRIBUTING.md)

## Rate Limiting

The REST API uses a Bucket4j filter to control how frequently clients can make
requests. Each authenticated user is allowed **30 requests per minute**. If a
request is missing a Discord token or the user id cannot be determined, the
filter falls back to rate limiting based on the caller's IP address so
unauthenticated requests cannot bypass the limit.
