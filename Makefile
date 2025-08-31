# RestMonkey Makefile
# Simple commands for common development tasks

.PHONY: test clean remove-emojis help

# Default target
help:
	@echo "RestMonkey Development Commands:"
	@echo ""
	@echo "  make test          - Run all tests"
	@echo "  make clean         - Clean build artifacts"
	@echo "  make remove-emojis - Remove emoji icons from codebase"
	@echo "  make help          - Show this help"

# Run tests
test:
	mvn test

# Clean build
clean:
	mvn clean

# Remove emojis from codebase
remove-emojis:
ifeq ($(OS),Windows_NT)
	@echo "Running PowerShell emoji removal script..."
	powershell -ExecutionPolicy Bypass -File scripts/remove-emojis.ps1
else
	@echo "Running shell emoji removal script..."
	chmod +x scripts/remove-emojis.sh
	./scripts/remove-emojis.sh
endif
	@echo "Testing after emoji removal..."
	mvn test -q
