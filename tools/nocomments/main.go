package main

import (
	"bufio"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

func main() {
	root := "."
	if len(os.Args) > 1 {
		root = os.Args[1]
	}

	failed := false
	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			name := d.Name()
			if name == ".git" || name == ".gradle" || name == "build" || name == "bin" || name == "tools" || name == "testdata" {
				return filepath.SkipDir
			}
			return nil
		}

		ext := filepath.Ext(path)
		name := d.Name()
		if ext != ".java" && ext != ".kts" && ext != ".gradle" && ext != ".sql" && ext != ".yaml" && ext != ".yml" && ext != ".properties" && ext != ".sh" && name != "Makefile" && name != "Dockerfile" {
			return nil
		}

		file, err := os.Open(path)
		if err != nil {
			fmt.Fprintf(os.Stderr, "failed to open %s: %v\n", path, err)
			failed = true
			return nil
		}
		defer file.Close()

		scanner := bufio.NewScanner(file)
		lineNum := 0
		inBlockComment := false
		for scanner.Scan() {
			lineNum++
			line := scanner.Text()
			trimmed := strings.TrimSpace(line)

			if ext == ".properties" {
				if strings.HasPrefix(trimmed, "#") || strings.HasPrefix(trimmed, "!") {
					fmt.Fprintf(os.Stderr, "%s:%d: forbidden comment in properties: %s\n", path, lineNum, trimmed)
					failed = true
				}
				continue
			}

			if name == "Makefile" || name == "Dockerfile" || ext == ".sh" || ext == ".yaml" || ext == ".yml" {
				if strings.HasPrefix(trimmed, "#") {
					fmt.Fprintf(os.Stderr, "%s:%d: forbidden comment in %s: %s\n", path, lineNum, name, trimmed)
					failed = true
				}
				continue
			}

			if inBlockComment {
				fmt.Fprintf(os.Stderr, "%s:%d: inside forbidden block comment\n", path, lineNum)
				failed = true
				if strings.Contains(line, "*/") {
					inBlockComment = false
				}
				continue
			}

			cleanLine := stripQuotedLiterals(line)
			if strings.Contains(cleanLine, "/*") {
				fmt.Fprintf(os.Stderr, "%s:%d: forbidden block comment: %s\n", path, lineNum, line)
				failed = true
				if !strings.Contains(cleanLine, "*/") {
					inBlockComment = true
				}
				continue
			}

			if strings.Contains(cleanLine, "//") {
				fmt.Fprintf(os.Stderr, "%s:%d: forbidden line comment: %s\n", path, lineNum, line)
				failed = true
			}
		}
		return nil
	})

	if err != nil {
		fmt.Fprintf(os.Stderr, "walk failed: %v\n", err)
		os.Exit(2)
	}

	if failed {
		os.Exit(1)
	}
}

func stripQuotedLiterals(s string) string {
	var sb strings.Builder
	inQuote := false
	var quoteChar rune
	escaped := false

	for _, r := range s {
		if escaped {
			escaped = false
			continue
		}
		if r == '\\' && inQuote {
			escaped = true
			continue
		}
		if inQuote {
			if r == quoteChar {
				inQuote = false
			}
			continue
		}
		if r == '"' || r == '\'' {
			inQuote = true
			quoteChar = r
			continue
		}
		sb.WriteRune(r)
	}
	return sb.String()
}
