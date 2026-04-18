with open(".github/workflows/build-release.yml", "r") as f:
    content = f.read()

# Fix setup-java caching - cache parameter wasn't valid in older versions, and gradle action is used later. But it's valid in v4, however, gradle-build-action is deprecated. Using setup-java caching is the modern way. Let's fix the workflow to be modern and correct.
content = content.replace("""      - name: Setup Java JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: gradle

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v3
        with:
          gradle-version: wrapper""", """      - name: Setup Java JDK
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: gradle""")

# Fix base64 -d which might fail if the input has newlines. Actually `base64 -d` works. But sometimes `-w 0` is needed.
# Let's leave base64 as is.

with open(".github/workflows/build-release.yml", "w") as f:
    f.write(content)
