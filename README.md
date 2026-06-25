# 🌌 Insight Launcher (インサイト・ランチャー)

<div align="center">
  <p><b>Gemini API を搭載した、AI世代 of Android向け近未来型ホームランチャー</b></p>
  <a href="https://github.com/dma-cmyk/insight-launcher/releases">
    <img src="https://img.shields.io/github/v/release/dma-cmyk/insight-launcher?style=for-the-badge&color=6B5B95" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language" />
</div>

---

**Insight Launcher** は、Google Gemini API を活用し、インストールされているアプリの「意味」や「用途」を自動で解析・スマートに分類する、全く新しいAndroid用ランチャーアプリです。
美しい宇宙空間のダイナミック背景（Space Background）に包まれたUIと、AIアシスタント機能があなたのスマートフォンライフをスマートに彩ります。

---

## 📸 スクリーンショット

<div align="center">
  <table border="0">
    <tr>
      <td align="center"><b>リストビュー (List View)</b><br>アプリ詳細とAIの解析結果を表示</td>
      <td align="center"><b>グリッドビュー (Grid View)</b><br>スッキリ並ぶ3列レイアウト（解析バッジ付）</td>
    </tr>
    <tr>
      <td valign="top"><img src="docs/images/screenshot_list.png" width="300" alt="List View" /></td>
      <td valign="top"><img src="docs/images/screenshot_grid.png" width="300" alt="Grid View" /></td>
    </tr>
  </table>
</div>

---

## ✨ 主な機能

* **🤖 AIによるアプリの自動分類**
  * Gemini API がアプリの名前やパッケージ情報を解析し、「仕事効率化」「ツール」「エンタメ」などのカテゴリに自動でスマート分類します。
* **🔗 類似カテゴリのAI統合機能 [NEW]**
  * AIが自動で細分化されてしまったカテゴリ（例:「チャット」と「メッセンジャー」など）の意味を分析し、**自動で1つのカテゴリへ統合**します。設定画面からワンタップで実行可能です。
* **🔍 セマンティックアプリ検索 (AI意図解析) & 類似度スコア表示**
  * 「カレンダー」のようなアプリ名での検索はもちろん、「予定を管理したい」「ネットサーフィンしたい」といった自然言語の意図を入力して、最適なアプリをAIが探し出します。
  * 検索時には、AIが算出した**類似度スコア（Similarity Score）**がバッジとして表示され、マッチングの度合いが視覚的に分かります。
  * まだAIによる解析が行われていないアプリには、未解析バッジが表示されます。
* **🌌 SFチックな宇宙テーマ UI**
  * 美しくまたたく星々と、滑らかなアニメーションで近未来感を演出するダイナミックな宇宙背景。
* **🔄 切り替え可能なレイアウト**
  * アプリの詳細説明とAI解析バッジが表示される「リストビュー」と、スッキリとスマートに並ぶ「グリッドビュー」をワンタップで切り替え可能です。
* **⚙️ アプリシステム設定へのダイレクトアクセス**
  * アプリ詳細ダイアログから、そのアプリのAndroid「システム設定（アプリ情報）」画面へワンタップで直接遷移できるようになりました。通知設定や権限の変更がスムーズに行えます。
* **🌐 ローカライズと言語切り替えの最適化**
  * 英語、日本語、韓国語、中国語などの多言語表示に対応。AI解析ステータスやバッジ、未解析アプリ警告表示なども設定された言語に合わせてローカライズされます。
* **⚙️ モデル＆エンジンの自由なカスタマイズ**
  * 設定画面から、解析に使用する `LLM (Gemini)` のモデルや `Embedding` モデルを自由に変更可能です。

---

## 🛠️ ローカルでの開発 & ビルド方法

### 前提条件
* **Android Studio** (最新版推奨)
* **JDK 17** (ビルドに必要です。`mise` 等での管理を推奨)
* **Android SDK** (API 24以上、Target API 36)

### セットアップ手順

1. **プロジェクトのインポート**
   Android Studio を開き、本プロジェクトのルートディレクトリをインポートします。

2. **APIキーの設定 (`.env` ファイルの作成)**
   プロジェクトのルートディレクトリに `.env` ファイルを作成し、Gemini APIキーを設定してください。
   ```bash
   cp .env.example .env
   ```
   `.env` ファイルを開き、以下のようにキーを入力します：
   ```env
   GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
   ```

3. **SDKパスの確認 (`local.properties`)**
   `local.properties` を作成または編集し、お使いの環境の Android SDK のパスを指定してください。
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   ```

4. **ビルド & 実行**
   ```bash
   # デバッグビルド (APKの生成)
   ./gradlew assembleDebug
   ```

---

## 📲 インストール方法 (APK)

すぐにスマホで使ってみたい場合は、[Releases](https://github.com/dma-cmyk/insight-launcher/releases) ページから最新のコンパイル済み APK ファイル（`app-debug.apk`）をダウンロードしてインストールしてください。

1. **[リリースページ](https://github.com/dma-cmyk/insight-launcher/releases) にアクセス**
2. 最新リリースの `Assets` から `app-debug.apk` をダウンロード
3. スマホに転送し、「不明なソースからのアプリ」のインストールを許可してインストール

---

## 🛡️ ライセンス & 免責事項
このプロジェクトは Google AI Studio のコード生成をベースに構築されています。
