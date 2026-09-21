# Project website

The source of <https://spartanlabsgaming.github.io/MyGameTools/> — a static, dependency-free
landing page for GameTools. There is no build step and no site generator: the files in this
directory are published as they are.

| File | What it is |
|---|---|
| `index.html` | The whole page — hero, features, architecture, modules, install, quick start, testing, links |
| `assets/styles.css` | One stylesheet. Colours are CSS custom properties on `:root`, redefined for `[data-theme="light"]` |
| `assets/site.js` | Theme toggle, mobile nav, install tabs, copy buttons, scroll-spy, and a ~60-line syntax highlighter. The page stays readable if it never loads |
| `assets/favicon.svg` | Favicon |
| `404.html` | Not-found page (absolute `/MyGameTools/…` paths, because GitHub serves it from any depth) |
| `.nojekyll` | Stops GitHub Pages running the published output through Jekyll, which would drop Dokka's `_`-prefixed files |
| `robots.txt`, `sitemap.xml` | Crawler hints |

## How it is published

[`.github/workflows/pages.yml`](../.github/workflows/pages.yml) runs on every push to `master`.
It generates the Dokka publication (`./gradlew dokkaGeneratePublicationHtml`), copies this
directory to the site root and the Dokka output to `api/`, then deploys with
`actions/deploy-pages`. So `/` is this page and `/api/` is the full API reference.

Enabling it once, in the repository settings: **Settings → Pages → Build and deployment →
Source → GitHub Actions**.

## Working on it locally

Any static file server will do — the page uses relative paths:

```bash
python3 -m http.server 8000 --directory website
# then open http://localhost:8000/
```

`/api/` will 404 locally unless you generate the docs into it first:

```bash
./gradlew dokkaGeneratePublicationHtml
cp -r build/dokka/html website/api      # git-ignored; do not commit
```

## When cutting a release

The version appears in the page as the nav pill, the install line under **Install**, and the
Maven/Groovy snippets — all marked with `data-version` or spelled out in the snippets. Bump
them together with the `coordinates(...)` lines in the module build files:

```bash
grep -rn "5\.1\.0" website/index.html
```
