package(default_visibility = ["//visibility:public"])

cc_library(
    name = "slicer",
    srcs = [
        "bytecode_encoder.cc",
        "code_ir.cc",
        "common.cc",
        "control_flow_graph.cc",
        "debuginfo_encoder.cc",
        "dex_bytecode.cc",
        "dex_format.cc",
        "dex_ir.cc",
        "dex_ir_builder.cc",
        "dex_utf8.cc",
        "instrumentation.cc",
        "reader.cc",
        "tryblocks_encoder.cc",
        "writer.cc",
    ],
    hdrs = [
        "arrayview.h",
        "buffer.h",
        "bytecode_encoder.h",
        "chronometer.h",
        "code_ir.h",
        "common.h",
        "control_flow_graph.h",
        "debuginfo_encoder.h",
        "dex_bytecode.h",
        "dex_format.h",
        "dex_ir.h",
        "dex_ir_builder.h",
        "dex_leb128.h",
        "dex_utf8.h",
        "hash_table.h",
        "index_map.h",
        "instrumentation.h",
        "intrusive_list.h",
        "memview.h",
        "reader.h",
        "scopeguard.h",
        "tryblocks_encoder.h",
        "writer.h",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "@zlib_repo//:zlib",
    ],
)
