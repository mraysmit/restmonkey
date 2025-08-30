# TinyRest Utility Scripts

This directory contains utility scripts for maintaining the TinyRest codebase.

## remove-emojis Scripts

These scripts remove emoji icons from the TinyRest codebase and documentation, providing clean, professional output suitable for enterprise environments.

### Windows (PowerShell)

```powershell
# Run from project root
.\scripts\remove-emojis.ps1
```

**Features:**
- Works with Windows PowerShell and PowerShell Core
- Cross-platform compatible (Windows, Linux, macOS)
- Processes Java source code and documentation files
- Replaces Unicode arrows with ASCII equivalents
- Provides colored output for progress tracking

### Linux/macOS (Bash)

```bash
# Make executable (Linux/macOS only)
chmod +x scripts/remove-emojis.sh

# Run from project root
./scripts/remove-emojis.sh
```

**Features:**
- Uses `perl` for reliable Unicode handling
- Handles Unicode arrow replacement
- Creates backup files (.bak) for safety
- Works on all Unix-like systems

### Files Processed

Both scripts process these files:
- `src/main/java/dev/mars/tinyrest/TinyRest.java` - Main source code
- `docs/README.md` - Project documentation
- `docs/LOGGING.md` - Logging configuration guide
- `docs/LOGGING_EXAMPLES.md` - Logging examples

### Usage Notes

1. **Run from project root**: Both scripts expect to be run from the TinyRest project root directory
2. **Test after running**: Always run `mvn test` after emoji removal to verify functionality
3. **Version control**: Commit changes after running to preserve the clean codebase
4. **Safe operation**: Scripts only remove visual emoji characters, not functional code

### Example Output

**Before:**
```java
log.info("Server started successfully");
httpLog.info("-> GET /health");
```

**After:**
```java
log.info("Server started successfully");
httpLog.info("-> GET /health");
```

The logging functionality remains identical - only visual emoji indicators are removed for a more professional appearance.
