# Demo walkthrough

A reproducible way to show Kaidoku in about 60 seconds, and to record the README GIF.

## 1. Start the app

```bash
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run`. Wait for `Started KaidokuApplication`, then open http://localhost:8080 in a browser window about 1200 px wide.

## 2. Script

1. **Load example.** It fills in a built-in paragraph and analyzes it: 13 highlights from 12 different patterns across N5–N3. Point out the colours per level and the "Patterns found" list with counts.
2. **Click 考えれば考えるほど.** The card shows ～ば～ほど (N3), its meaning, an explanation and examples.
3. **Untick N3.** The N3 highlights disappear and the list updates. Tick it again.
4. **Paste a sentence with overlapping patterns.** The underline marks the nested match, and clicking it lists both patterns.

   ```
   彼は東京に住んでいることになっている。
   ```

5. **Paste a sentence that shows the ambiguity handling.** The card for 春らしい explains both readings of らしい instead of guessing.

   ```
   春らしい天気ですね。もう行かなきゃ。
   ```

## 3. Recording the GIF

- **Tool:** ScreenToGif on Windows, Kap on macOS, or Peek on Linux.
- **Capture area:** just the browser content area, about 1200 × 800, at 10–15 fps. Keep the clip under 20 seconds and the file under 5 MB, so it loads quickly on GitHub.
- **What to record:** steps 1–3 above. Pause about one second after each click so viewers can read the card.
- **Saving:** save it as `docs/demo.gif`, then replace the `<!-- demo-gif: … -->` comment below the screenshot in the README with `![Demo](docs/demo.gif)`.

## 4. API demo

```bash
curl -s -X POST http://localhost:8080/api/analyze -H 'Content-Type: application/json' -d '{"text": "日本に来たばかりです。"}'
```

```bash
curl -s "http://localhost:8080/api/patterns?level=N5"
```

On Windows, Git Bash's curl can garble Japanese typed on the command line. Send the JSON from a UTF-8 file with `--data-binary @body.json`, or use the `\uXXXX` escapes in the JSON instead.
