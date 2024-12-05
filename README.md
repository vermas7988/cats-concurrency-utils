# 🚀 Common Concurrency utils with cats effect


## 📖 Overview

- The `RequestRegistry` is a utility to manage asynchronous requests and responses. It leverages Cats Effect's `Deferred` to handle responses in a decoupled manner, allowing parts of a program to submit some requestIDs and other parts to receive responses and satisfy the requests submitted in this registry.
- `LoadBalancer` is a utility to divide and queue work to different servers.

Docs WIP but code is readable and api is user-friendly.

Not published on maven yet, just copy paste the examples and tailor according to your project.

## ✨ Features

- 📝 Register requests and await responses.
- ✅ Complete requests with responses from different parts of the program.
- 🛠 Built using Cats Effect.

## 🛠️ Usage

### 📦 Adding the Dependency

To use the `RequestRegistry` library in your project, you need to add the Cats Effect library to your `build.sbt` file.

## ⚠️ Caution
**Note:** This is not a managed library and rather use it as a code example. You can adapt and integrate this code into your projects as needed.

## 📜 License
This library is licensed under the MIT License (not really, it is just a code example).
