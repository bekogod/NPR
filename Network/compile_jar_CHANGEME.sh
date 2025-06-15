#!/bin/bash
set -e

# Define base directories
BASE_DIR="../NPR-2425/Network" # CHANGE THIS TO YOUR BASE DIR
TARGET_DIR="scenarios/vehApp/application" # CHANGE THIS TO YOUR TARGET DIR

# Remove all previous JAR files in the target directory
find $TARGET_DIR -name "*.jar" -exec rm -f {} \;

# Array of modules to build
modules=("OBU" "RSU" "FOG" "OBU_EmergV")

# Build each module and copy the resulting JARs
for module in "${modules[@]}"; do
    echo "Building module: $module"
    mvn clean package -f "$BASE_DIR/$module/pom.xml"
    
    # Check if the JAR file exists before attempting to copy
    JAR_FILE="$BASE_DIR/$module/target/*.jar"
    if [ -e $JAR_FILE ]; then
        cp $JAR_FILE $TARGET_DIR/
        echo "Copied JARs for $module"
    else
        echo "No JAR files found for $module. Skipping copy."
    fi
done

echo "All modules built and JARs copied successfully!"
