/* GameTools — project site behaviour.
   No dependencies: theme toggle, mobile nav, tabs, copy buttons, scroll-spy and a
   small syntax highlighter for the code samples. Everything degrades to plain,
   readable HTML if this file never loads. */
(function () {
  "use strict";

  // #region theme
  var root = document.documentElement;
  var STORE_KEY = "gametools-theme";

  function storedTheme() {
    try { return localStorage.getItem(STORE_KEY); } catch (e) { return null; }
  }
  function storeTheme(value) {
    try { localStorage.setItem(STORE_KEY, value); } catch (e) { /* private mode — ignore */ }
  }

  var saved = storedTheme();
  if (saved === "light" || saved === "dark") {
    root.setAttribute("data-theme", saved);
  } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches) {
    root.setAttribute("data-theme", "light");
  }

  var themeButton = document.querySelector(".theme-toggle");
  if (themeButton) {
    themeButton.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "light" ? "dark" : "light";
      root.setAttribute("data-theme", next);
      storeTheme(next);
    });
  }
  // #endregion

  // #region mobile nav
  var navToggle = document.querySelector(".nav-toggle");
  var nav = document.getElementById("site-nav");
  if (navToggle && nav) {
    navToggle.addEventListener("click", function () {
      var open = nav.classList.toggle("open");
      navToggle.setAttribute("aria-expanded", String(open));
    });
    nav.addEventListener("click", function (event) {
      if (event.target.closest("a")) {
        nav.classList.remove("open");
        navToggle.setAttribute("aria-expanded", "false");
      }
    });
  }
  // #endregion

  // #region tabs
  document.querySelectorAll("[data-tabs]").forEach(function (group) {
    var tabs = Array.prototype.slice.call(group.querySelectorAll('[role="tab"]'));

    function select(tab) {
      tabs.forEach(function (other) {
        var selected = other === tab;
        other.setAttribute("aria-selected", String(selected));
        other.tabIndex = selected ? 0 : -1;
        var panel = document.getElementById(other.getAttribute("aria-controls"));
        if (panel) { panel.hidden = !selected; }
      });
    }

    tabs.forEach(function (tab, index) {
      tab.addEventListener("click", function () { select(tab); });
      tab.addEventListener("keydown", function (event) {
        var step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
        if (!step) { return; }
        event.preventDefault();
        var next = tabs[(index + step + tabs.length) % tabs.length];
        select(next);
        next.focus();
      });
    });
  });
  // #endregion

  // #region copy buttons
  document.querySelectorAll("[data-copy]").forEach(function (button) {
    button.addEventListener("click", function () {
      var card = button.closest(".code-card");
      var code = card && card.querySelector("pre");
      if (!code) { return; }

      var text = code.innerText;
      var done = function () {
        var original = button.getAttribute("data-label") || button.textContent;
        button.setAttribute("data-label", original);
        button.textContent = "Copied";
        button.classList.add("done");
        window.setTimeout(function () {
          button.textContent = original;
          button.classList.remove("done");
        }, 1600);
      };

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done, fallbackCopy);
      } else {
        fallbackCopy();
      }

      function fallbackCopy() {
        var area = document.createElement("textarea");
        area.value = text;
        area.setAttribute("readonly", "");
        area.style.position = "fixed";
        area.style.opacity = "0";
        document.body.appendChild(area);
        area.select();
        try { document.execCommand("copy"); done(); } catch (e) { /* nothing more to try */ }
        document.body.removeChild(area);
      }
    });
  });
  // #endregion

  // #region scroll-spy
  var navLinks = Array.prototype.slice.call(document.querySelectorAll('.site-nav a[href^="#"]'));
  var sections = navLinks
    .map(function (link) { return document.querySelector(link.getAttribute("href")); })
    .filter(Boolean);

  if (sections.length && "IntersectionObserver" in window) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) { return; }
        navLinks.forEach(function (link) {
          link.classList.toggle("active", link.getAttribute("href") === "#" + entry.target.id);
        });
      });
    }, { rootMargin: "-45% 0px -50% 0px" });
    sections.forEach(function (section) { observer.observe(section); });
  }
  // #endregion

  // #region highlighting
  /* Deliberately small: enough to colour the snippets on this page, not a general
     parser. Each pattern group maps to one token class; anything unmatched is left
     as escaped plain text. */
  var KOTLIN = new RegExp([
    "(\\/\\/[^\\n]*)",                                             // 1 line comment
    "(\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')", // 2 string
    "(@\\w+)",                                                     // 3 annotation
    "\\b(val|var|fun|class|object|interface|import|package|return|if|else|when|for|while|in|is|as|null|true|false|this|super|open|abstract|override|private|public|internal|protected|sealed|data|enum|companion|init|by|it|suspend|lateinit|const|typealias|implementation|api|dependencies|plugins)\\b", // 4 keyword
    "\\b(\\d[\\d_]*(?:\\.\\d+)?[LfFdD]?)\\b",                      // 5 number
    "\\b([A-Z][A-Za-z0-9_]*)\\b",                                  // 6 type
    "\\b([a-z][A-Za-z0-9_]*)(?=\\s*\\()"                           // 7 call
  ].join("|"), "g");

  var BASH = new RegExp([
    "(#[^\\n]*)",                                                  // 1 comment
    "(\"(?:\\\\.|[^\"\\\\\\n])*\"|'[^'\\n]*')",                    // 2 string
    "(^|\\n)(\\s*)(\\.\\/gradlew|[a-z][\\w.\\/-]*)(?=\\s|$)"       // 3-5 command word
  ].join("|"), "g");

  var XML = new RegExp([
    "(&lt;!--[\\s\\S]*?--&gt;)",                                   // 1 comment
    "(&lt;\\/?)([\\w:.-]+)",                                       // 2-3 tag
    "(&gt;)"                                                       // 4 close
  ].join("|"), "g");

  var CLASSES = {
    1: "tok-comment", 2: "tok-string", 3: "tok-annotation", 4: "tok-keyword",
    5: "tok-number", 6: "tok-type", 7: "tok-func"
  };

  function escapeHtml(value) {
    return value
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function span(cls, value) { return '<span class="' + cls + '">' + value + "</span>"; }

  function highlightKotlinLike(source) {
    var out = "";
    var last = 0;
    var match;
    KOTLIN.lastIndex = 0;
    while ((match = KOTLIN.exec(source)) !== null) {
      out += escapeHtml(source.slice(last, match.index));
      for (var group = 1; group <= 7; group++) {
        if (match[group] !== undefined) {
          out += span(CLASSES[group], escapeHtml(match[group]));
          break;
        }
      }
      last = match.index + match[0].length;
    }
    return out + escapeHtml(source.slice(last));
  }

  function highlightBash(source) {
    var out = "";
    var last = 0;
    var match;
    BASH.lastIndex = 0;
    while ((match = BASH.exec(source)) !== null) {
      out += escapeHtml(source.slice(last, match.index));
      if (match[1] !== undefined) {
        out += span("tok-comment", escapeHtml(match[1]));
      } else if (match[2] !== undefined) {
        out += span("tok-string", escapeHtml(match[2]));
      } else {
        out += escapeHtml(match[3] || "") + escapeHtml(match[4] || "") +
               span("tok-func", escapeHtml(match[5]));
      }
      last = match.index + match[0].length;
    }
    return out + escapeHtml(source.slice(last));
  }

  /* The XML sample is already HTML-escaped in the markup, so it is highlighted on the
     escaped text rather than re-escaped. */
  function highlightXml(escaped) {
    return escaped.replace(XML, function (whole, comment, open, name, close) {
      if (comment) { return span("tok-comment", comment); }
      if (open) { return span("tok-tag", open) + span("tok-attr", name); }
      return span("tok-tag", close);
    });
  }

  document.querySelectorAll("pre code[data-lang]").forEach(function (block) {
    var lang = block.getAttribute("data-lang");
    if (lang === "xml") {
      block.innerHTML = highlightXml(block.innerHTML);
    } else if (lang === "bash") {
      block.innerHTML = highlightBash(block.textContent);
    } else {
      block.innerHTML = highlightKotlinLike(block.textContent);
    }
  });
  // #endregion
})();
