load("//tools/base/bazel:android.bzl", "dex_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "android-mock",
    srcs = glob(["**/*.java"]),
    neverlink = 1,
    deps = [
        "//tools/idea/.idea/libraries:Guava",
    ],
)

dex_library(
    name = "android-mock-dex",
    jars = ["android-mock"],
)
