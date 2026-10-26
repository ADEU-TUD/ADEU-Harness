package io.github.thepytales.llmhub.files

/**
 * Which files are plain text (code, configuration, markup) and in which language they are written. Shared by the
 * attachment readers of both platforms and the tools for local folders.
 */
object TextFormats {
    /** Extensions of text files: read as they are (in the encoding of the file). */
    val TEXT_EXTENSIONS: Set<String> = setOf(
        // Text, data, markup
        "txt", "text", "md", "markdown", "rmd", "qmd", "csv", "tsv", "json", "jsonl", "ndjson", "geojson", "xml", "yaml", "yml",
        "toml", "ini", "cfg", "conf", "config", "log", "html", "htm", "xhtml", "css", "scss", "sass", "less", "tex", "bib", "sty",
        "cls", "dtx", "ltx", "typ", "srt", "vtt", "env", "properties", "gitignore", "gitattributes", "dockerignore", "editorconfig",
        "svg", "mmd", "mermaid", "puml", "plantuml", "dot", "gv", "rst", "adoc", "asciidoc", "org", "wiki", "diff", "patch",
        // Web and JavaScript
        "js", "mjs", "cjs", "ts", "mts", "cts", "jsx", "tsx", "vue", "svelte", "astro", "graphql", "gql", "proto", "prisma",
        // JVM, .NET, C family
        "kt", "kts", "java", "groovy", "gvy", "gradle", "scala", "sc", "clj", "cljs", "cljc", "edn", "c", "h", "cpp", "cc", "cxx",
        "hpp", "hh", "hxx", "cs", "csx", "fs", "fsx", "fsi", "vb", "vbs", "bas", "frm", "csproj", "vbproj", "fsproj", "sln",
        "xaml", "props", "targets", "resx", "cu", "cuh", "m", "mm", "swift", "d", "zig", "nim", "cr", "v", "sv", "svh", "vhd",
        "vhdl", "ino", "pde", "glsl", "hlsl", "wgsl", "frag", "vert", "asm", "s",
        // Scripting
        "py", "pyw", "pyi", "rb", "go", "rs", "php", "pl", "pm", "lua", "r", "jl", "dart", "sh", "bash", "zsh", "ksh", "fish",
        "ps1", "psm1", "psd1", "bat", "cmd", "awk", "sed", "tcl", "el", "lisp", "scm", "rkt", "ss",
        // Functional and other languages
        "hs", "lhs", "ex", "exs", "erl", "hrl", "elm", "ml", "mli", "f", "for", "f90", "f95", "f03", "pas", "pp", "lpr", "dpr",
        "ada", "adb", "ads", "cob", "cbl", "sol",
        // Statistics and data
        "sql", "psql", "sas", "do", "ado", "sps", "stan",
        // Build and infrastructure
        "tf", "tfvars", "hcl", "nix", "cmake", "mk", "make", "dockerfile", "containerfile", "jenkinsfile", "bazel", "bzl", "lock",
    )

    /** Files without extension (or with a special name) that are text. */
    private val TEXT_NAMES = setOf(
        "dockerfile", "containerfile", "makefile", "gnumakefile", "jenkinsfile", "procfile", "gemfile", "rakefile", "vagrantfile",
        "brewfile", "podfile", "fastfile", "justfile", "cmakelists.txt", "license", "licence", "readme", "changelog", "authors",
        "contributing", "copying", "notice", "todo", "codeowners", ".editorconfig", ".gitignore", ".gitattributes", ".gitmodules",
        ".dockerignore", ".npmrc", ".nvmrc", ".prettierrc", ".eslintrc", ".babelrc", ".env", ".bashrc", ".zshrc", ".profile",
        ".bash_profile", ".vimrc", ".htaccess", "requirements.txt", "pipfile", "go.mod", "go.sum", "cargo.lock",
    )

    /** Whether [name] (file name with extension) is a text file. */
    fun isText(name: String): Boolean {
        val lower = name.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return lower in TEXT_NAMES || ext in TEXT_EXTENSIONS || lower.startsWith(".env.") || lower.startsWith("dockerfile.") ||
            lower.endsWith(".dockerfile")
    }

    private val LANGUAGES = mapOf(
        "py" to "Python", "pyw" to "Python", "pyi" to "Python (Typen)", "ipynb" to "Jupyter-Notebook", "r" to "R", "rmd" to "R Markdown",
        "qmd" to "Quarto", "jl" to "Julia", "m" to "MATLAB/Objective-C", "sas" to "SAS", "do" to "Stata", "ado" to "Stata", "sps" to "SPSS-Syntax",
        "stan" to "Stan", "sql" to "SQL", "psql" to "PostgreSQL", "kt" to "Kotlin", "kts" to "Kotlin-Skript", "java" to "Java",
        "groovy" to "Groovy", "gvy" to "Groovy", "gradle" to "Gradle (Groovy)", "scala" to "Scala", "clj" to "Clojure", "cljs" to "ClojureScript",
        "c" to "C", "h" to "C/C++-Header", "cpp" to "C++", "cc" to "C++", "cxx" to "C++", "hpp" to "C++-Header", "cs" to "C#", "fs" to "F#",
        "fsx" to "F#-Skript", "fsi" to "F#-Signatur", "vb" to "Visual Basic", "vbs" to "VBScript", "bas" to "Visual Basic (Modul)",
        "cls" to "VBA-Klasse oder LaTeX-Klasse", "frm" to "Visual-Basic-Formular", "csproj" to "MSBuild (C#-Projekt)", "vbproj" to "MSBuild (VB-Projekt)",
        "sln" to "Visual-Studio-Projektmappe", "xaml" to "XAML", "props" to "MSBuild", "targets" to "MSBuild", "swift" to "Swift",
        "go" to "Go", "rs" to "Rust", "rb" to "Ruby", "php" to "PHP", "pl" to "Perl", "pm" to "Perl-Modul", "lua" to "Lua", "dart" to "Dart",
        "js" to "JavaScript", "mjs" to "JavaScript (ES-Modul)", "cjs" to "JavaScript (CommonJS)", "ts" to "TypeScript", "jsx" to "JavaScript (JSX)",
        "tsx" to "TypeScript (TSX)", "vue" to "Vue", "svelte" to "Svelte", "astro" to "Astro", "graphql" to "GraphQL", "gql" to "GraphQL",
        "proto" to "Protocol Buffers", "prisma" to "Prisma-Schema", "html" to "HTML", "htm" to "HTML", "css" to "CSS", "scss" to "SCSS",
        "sass" to "Sass", "less" to "Less", "sh" to "Shell-Skript", "bash" to "Bash", "zsh" to "Zsh", "ksh" to "KornShell", "fish" to "Fish",
        "ps1" to "PowerShell", "psm1" to "PowerShell-Modul", "psd1" to "PowerShell-Daten", "bat" to "Windows-Batch", "cmd" to "Windows-Batch",
        "awk" to "AWK", "sed" to "sed", "tcl" to "Tcl", "el" to "Emacs Lisp", "lisp" to "Lisp", "scm" to "Scheme", "rkt" to "Racket",
        "hs" to "Haskell", "ex" to "Elixir", "exs" to "Elixir-Skript", "erl" to "Erlang", "hrl" to "Erlang-Header", "elm" to "Elm",
        "ml" to "OCaml", "mli" to "OCaml-Signatur", "f" to "Fortran", "for" to "Fortran", "f90" to "Fortran 90", "f95" to "Fortran 95",
        "pas" to "Pascal", "pp" to "Pascal", "ada" to "Ada", "adb" to "Ada", "ads" to "Ada (Spezifikation)", "cob" to "COBOL", "cbl" to "COBOL",
        "d" to "D", "zig" to "Zig", "nim" to "Nim", "cr" to "Crystal", "v" to "Verilog/V", "sv" to "SystemVerilog", "vhd" to "VHDL",
        "vhdl" to "VHDL", "asm" to "Assembler", "s" to "Assembler", "cu" to "CUDA", "cuh" to "CUDA-Header", "glsl" to "GLSL", "hlsl" to "HLSL",
        "wgsl" to "WGSL", "sol" to "Solidity", "ino" to "Arduino", "pde" to "Processing", "tex" to "LaTeX", "sty" to "LaTeX-Paket",
        "dtx" to "LaTeX (dokumentiert)", "bib" to "BibTeX", "typ" to "Typst", "md" to "Markdown", "markdown" to "Markdown", "mmd" to "Mermaid",
        "mermaid" to "Mermaid", "puml" to "PlantUML", "json" to "JSON", "xml" to "XML", "yaml" to "YAML", "yml" to "YAML", "toml" to "TOML",
        "ini" to "INI", "tf" to "Terraform", "tfvars" to "Terraform-Variablen", "hcl" to "HCL", "nix" to "Nix", "cmake" to "CMake",
        "mk" to "Makefile", "csv" to "CSV", "tsv" to "TSV", "svg" to "SVG (XML)",
    )

    private val NAMED_LANGUAGES = mapOf(
        "dockerfile" to "Dockerfile", "containerfile" to "Dockerfile", "makefile" to "Makefile", "gnumakefile" to "Makefile",
        "jenkinsfile" to "Jenkins-Pipeline (Groovy)", "procfile" to "Procfile", "gemfile" to "Ruby (Gemfile)", "rakefile" to "Ruby (Rakefile)",
        "vagrantfile" to "Ruby (Vagrantfile)", "cmakelists.txt" to "CMake", ".editorconfig" to "EditorConfig",
    )

    /** Programming or markup language of [name] for a label line, null for plain text. */
    fun language(name: String): String? {
        val lower = name.lowercase()
        NAMED_LANGUAGES[lower]?.let { return it }
        if (lower.startsWith("dockerfile.") || lower.endsWith(".dockerfile")) return "Dockerfile"
        return LANGUAGES[lower.substringAfterLast('.', "")]
    }

    /** Audio and video files (offered for transcription). */
    val MEDIA = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "wma", "mp4", "m4v", "mov", "webm", "mkv", "avi", "wmv", "mpeg", "mpg")

    /** Pictures; svg is also text. */
    val IMAGES = setOf("png", "jpg", "jpeg", "gif", "bmp", "wbmp", "webp", "tif", "tiff", "heic", "heif", "avif", "ico")
}
