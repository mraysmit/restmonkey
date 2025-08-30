#!/bin/bash
# Shell script to remove emoji icons from TinyRest codebase
# Works on Linux, macOS, and other Unix-like systems

set -e  # Exit on any error

echo "Removing emoji icons from TinyRest codebase..."

# Define files to process
files=(
    "src/main/java/dev/mars/tinyrest/TinyRest.java"
    "docs/README.md"
    "docs/LOGGING.md"
    "docs/LOGGING_EXAMPLES.md"
)

# Function to remove emojis from a file
remove_emojis() {
    local file="$1"

    if [[ ! -f "$file" ]]; then
        echo "  File not found: $file"
        return
    fi

    echo "Processing: $file"

    # Use perl for reliable Unicode handling
    # Remove emoji ranges and replace arrows
    perl -i.bak -pe '
        s/[\x{1F300}-\x{1F9FF}]//g;  # Misc Symbols and Pictographs
        s/[\x{2600}-\x{26FF}]//g;    # Misc Symbols
        s/[\x{2700}-\x{27BF}]//g;    # Dingbats
        s/\x{2192}/->/g;             # → to ->
        s/\x{2190}/<-/g;             # ← to <-
    ' "$file"

    echo "  Cleaned $file"
}

# Process each file
for file in "${files[@]}"; do
    remove_emojis "$file"
done

echo "Emoji removal complete!"
echo "Run 'mvn test' to verify everything still works."

# Note: Backup files (.bak) are created automatically by perl -i.bak
# Remove them if the operation was successful:
echo "Cleaning up backup files..."
find . -name "*.bak" -type f -delete 2>/dev/null || true
echo "Done!"
