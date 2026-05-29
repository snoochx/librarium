const firebaseConfig = {
  apiKey: "AIzaSyC60VNE8kQ-fsyYY4RAF-el4eaVxQ2orsU",
  authDomain: "librarium-dd543.firebaseapp.com",
  projectId: "librarium-dd543",
  storageBucket: "librarium-dd543.firebasestorage.app",
  messagingSenderId: "654268291589",
  appId: "1:654268291589:web:39220299091e1b3e3e9a6c",
  measurementId: "G-J7QDHKJNN1"
};

let firebaseApp = null;
let firebaseAuth = null;
let catalogRequestAbort = null;
let catalogRequestSeq = 0;

function loadScript(src) {
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = src;
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

async function ensureFirebase() {
  if (firebaseApp && firebaseAuth) {
    return;
  }

  if (!window.firebase) {
    await loadScript("https://www.gstatic.com/firebasejs/11.6.1/firebase-app-compat.js");
    await loadScript("https://www.gstatic.com/firebasejs/11.6.1/firebase-auth-compat.js");
  }

  firebaseApp = firebase.initializeApp(firebaseConfig);
  firebaseAuth = firebase.auth();
  await firebaseAuth.setPersistence(firebase.auth.Auth.Persistence.LOCAL);
}

async function postToken(idToken) {
  const response = await fetch("/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ idToken })
  });

  if (!response.ok) {
    throw new Error(await response.text());
  }

  return response.json();
}

function waitForFirebaseUser() {
  return new Promise((resolve) => {
    const unsubscribe = firebaseAuth.onAuthStateChanged((user) => {
      unsubscribe();
      resolve(user || null);
    });
  });
}

function authTargetForRole(role) {
  return "/profile";
}

function syncAuthenticatedTopbar(profile) {
  const actions = document.querySelector('.site-nav__actions');
  if (!actions || !profile) {
    return;
  }

  const target = authTargetForRole(profile.role);
  const existingUserLink = actions.querySelector('.site-nav__user');
  if (existingUserLink) {
    existingUserLink.href = target;
    existingUserLink.textContent = profile.displayName || existingUserLink.textContent || 'Профиль';
    return;
  }

  const loginButton = actions.querySelector('.site-nav__button');
  if (loginButton) {
    const userLink = document.createElement('a');
    userLink.className = 'site-nav__user';
    userLink.href = target;
    userLink.textContent = profile.displayName || 'Профиль';
    loginButton.replaceWith(userLink);
  }
}

async function restoreRememberedSession() {
  try {
    const response = await fetch("/auth/me", { credentials: "same-origin" });
    if (response.ok) {
      const profile = await response.json();
      syncAuthenticatedTopbar(profile);

      if (["/login", "/register"].includes(window.location.pathname)) {
        window.location.replace(authTargetForRole(profile.role));
      }

      return profile;
    }
  } catch (error) {
    console.error(error);
  }

  await ensureFirebase();

  const currentUser = await waitForFirebaseUser();
  if (!currentUser) {
    return null;
  }

  try {
    const idToken = await currentUser.getIdToken(true);
    const profile = await postToken(idToken);
    syncAuthenticatedTopbar(profile);

    if (["/login", "/register"].includes(window.location.pathname)) {
      window.location.replace(authTargetForRole(profile.role));
    }

    return profile;
  } catch (error) {
    console.error(error);
    return null;
  }
}

async function loginWithGoogle() {
  await ensureFirebase();

  const provider = new firebase.auth.GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });

  try {
    const result = await firebaseAuth.signInWithPopup(provider);
    const idToken = await result.user.getIdToken();
    const profile = await postToken(idToken);
    window.location.href = authTargetForRole(profile.role);
  } catch (error) {
    console.error(error);
  }
}

async function logoutFirebase() {
  try {
    await ensureFirebase();
    await fetch("/auth/logout", { method: "POST" });
    await firebaseAuth.signOut();
  } catch (error) {
    console.error(error);
  } finally {
    window.location.href = "/";
  }
}

function updateProfileRings() {
  document.querySelectorAll("[data-profile-ring]").forEach((ring) => {
    const read = Number(ring.dataset.readCount || "0");
    const planned = Number(ring.dataset.plannedCount || "0");
    const total = Number(ring.dataset.totalCount || String(read + planned || 0));
    const safeTotal = Number.isFinite(total) && total > 0 ? total : 0;
    const readAngle = safeTotal > 0 ? (Math.max(read, 0) / safeTotal) * 360 : 0;
    const plannedAngle = safeTotal > 0 ? (Math.max(planned, 0) / safeTotal) * 360 : 0;

    ring.style.setProperty("--read-angle", `${readAngle}deg`);
    ring.style.setProperty("--planned-angle", `${plannedAngle}deg`);
  });
}

function ensureToastContainer() {
  let container = document.querySelector("[data-toast-container]");
  if (container) {
    return container;
  }

  container = document.createElement("div");
  container.className = "app-toast-container";
  container.dataset.toastContainer = "true";
  container.setAttribute("aria-live", "polite");
  container.setAttribute("aria-atomic", "true");
  document.body.appendChild(container);
  return container;
}

function showToast(message) {
  if (!message) {
    return;
  }

  const container = ensureToastContainer();
  const toast = document.createElement("div");
  toast.className = "app-toast";
  toast.setAttribute("role", "status");

  const bar = document.createElement("div");
  bar.className = "app-toast__bar";

  const text = document.createElement("div");
  text.className = "app-toast__message";
  text.textContent = message;

  toast.appendChild(bar);
  toast.appendChild(text);
  container.appendChild(toast);

  window.setTimeout(() => {
    toast.classList.add("app-toast--leaving");
  }, 2800);

  window.setTimeout(() => {
    toast.remove();
    if (container.childElementCount === 0) {
      container.remove();
    }
  }, 3200);
}

function setupFlashToasts() {
  document.querySelectorAll("[data-toast-message]").forEach((node) => {
    const message = node.textContent.trim();
    node.remove();
    showToast(message);
  });
}

function catalogSortLabel(value) {
  const map = {
    newest: "Новое",
    rating: "Лучшее",
    author: "По автору",
    title: "По названию"
  };
  return map[value] || "Новое";
}

function buildCatalogUrl(form, page = 0) {
  const params = new URLSearchParams();

  const shell = form.closest("[data-catalog-shell]") || document.querySelector("[data-catalog-shell]");
  const basePath = shell?.dataset.catalogBase || "/catalog";

  const searchInput = form.querySelector("[data-catalog-search]");
  const sortInput = form.querySelector("[data-catalog-sort-value]");

  const query = searchInput ? searchInput.value.trim() : "";
  const sort = sortInput ? (sortInput.value || "newest") : "newest";

  if (query) {
    params.set("q", query);
  }
  if (sort) {
    params.set("sort", sort);
  }
  if (page > 0) {
    params.set("page", String(page));
  }

  const qs = params.toString();
  return qs ? `${basePath}?${qs}` : basePath;
}

async function loadCatalog(url, { historyMode = "push" } = {}) {
  const shell = document.querySelector("[data-catalog-shell]");
  if (!shell) {
    return;
  }

  if (catalogRequestAbort) {
    catalogRequestAbort.abort();
  }
  catalogRequestAbort = new AbortController();
  const requestId = ++catalogRequestSeq;

  try {
    const response = await fetch(url, {
      headers: { "X-Requested-With": "fetch" },
      signal: catalogRequestAbort.signal
    });
    if (!response.ok) {
      throw new Error(`Catalog request failed: ${response.status}`);
    }

    const html = await response.text();
    if (requestId !== catalogRequestSeq) {
      return;
    }
    const doc = new DOMParser().parseFromString(html, "text/html");

    const newCounter = doc.querySelector("[data-catalog-counter]");
    const newGrid = doc.querySelector("[data-catalog-grid]");
    const newPagination = doc.querySelector("[data-catalog-pagination]");
    const newSortValue = doc.querySelector("[data-catalog-sort-value]");

    const counter = shell.querySelector("[data-catalog-counter]");
    const grid = shell.querySelector("[data-catalog-grid]");
    const pagination = shell.querySelector("[data-catalog-pagination]");
    const sortValue = shell.querySelector("[data-catalog-sort-value]");
    const sortLabel = shell.querySelector("[data-catalog-sort-label]");

    if (counter && newCounter) {
      counter.innerHTML = newCounter.innerHTML;
    }
    if (grid && newGrid) {
      grid.innerHTML = newGrid.innerHTML;
    }
    if (pagination && newPagination) {
      pagination.innerHTML = newPagination.innerHTML;
    }
    if (sortValue && newSortValue) {
      sortValue.value = newSortValue.value;
    }
    if (sortLabel && sortValue) {
      sortLabel.textContent = catalogSortLabel(sortValue.value || "newest");
    }

    const nextUrl = new URL(url, window.location.origin);
    if (historyMode === "push") {
      window.history.pushState({ catalogUrl: nextUrl.pathname + nextUrl.search }, "", nextUrl.pathname + nextUrl.search);
    } else if (historyMode === "replace") {
      window.history.replaceState({ catalogUrl: nextUrl.pathname + nextUrl.search }, "", nextUrl.pathname + nextUrl.search);
    }
  } catch (error) {
    if (error.name === "AbortError") {
      return;
    }
    console.error(error);
    window.location.href = url;
  }
}

function setupProfileSettings() {
  const modal = document.querySelector("[data-profile-settings-modal]");
  const openButton = document.querySelector("[data-profile-settings-open]");
  const closeTargets = document.querySelectorAll("[data-profile-settings-close]");
  const input = document.querySelector("[data-profile-display-name-input]");
  const displayName = document.querySelector("[data-profile-name-display]");
  const userLink = document.querySelector(".site-nav__user");

  if (!modal || !input) {
    return;
  }

  let saveTimer = null;
  let lastSavedValue = (input.value || "").trim();

  const syncDisplayedName = (value) => {
    const text = value.trim() || lastSavedValue || input.placeholder || "Пользователь";
    if (displayName) {
      displayName.textContent = text;
    }
    if (userLink) {
      userLink.textContent = text;
    }
  };

  const saveName = async () => {
    const value = input.value.trim();
    if (value === lastSavedValue) {
      return;
    }

    try {
      const response = await fetch("/profile/name", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
          "X-Requested-With": "fetch"
        },
        body: new URLSearchParams({ displayName: value }).toString()
      });

      if (!response.ok) {
        throw new Error(`Name save failed: ${response.status}`);
      }

      lastSavedValue = value;
      syncDisplayedName(value);
    } catch (error) {
      console.error(error);
    }
  };

  const scheduleSave = () => {
    if (saveTimer) {
      window.clearTimeout(saveTimer);
    }
    saveTimer = window.setTimeout(saveName, 450);
  };

  const openModal = () => {
    modal.hidden = false;
    input.focus();
  };

  const closeModal = () => {
    modal.hidden = true;
  };

  if (openButton) {
    openButton.addEventListener("click", openModal);
  }

  closeTargets.forEach((target) => {
    target.addEventListener("click", closeModal);
  });

  input.addEventListener("input", () => {
    syncDisplayedName(input.value);
    scheduleSave();
  });

  input.addEventListener("change", saveName);
  input.addEventListener("blur", saveName);

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) {
      closeModal();
    }
  });
}


function setupProfileBooksTabs() {
  const root = document.querySelector("[data-profile-books-tabs]");
  if (!root) {
    return;
  }

  const buttons = Array.from(root.querySelectorAll("[data-profile-books-tab]"));
  const panes = Array.from(root.querySelectorAll("[data-profile-books-pane]"));

  const activate = (tabName) => {
    buttons.forEach((button) => {
      const active = button.dataset.profileBooksTab === tabName;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });

    panes.forEach((pane) => {
      const active = pane.dataset.profileBooksPane === tabName;
      pane.hidden = !active;
      pane.classList.toggle("is-active", active);
    });
  };

  buttons.forEach((button) => {
    button.addEventListener("click", () => {
      activate(button.dataset.profileBooksTab || "read");
    });
  });

  const initiallyActive = buttons.find((button) => button.classList.contains("is-active"))
    || buttons[0];

  if (initiallyActive) {
    activate(initiallyActive.dataset.profileBooksTab || "read");
  }
}


function setupReviewForm() {
  const form = document.querySelector("[data-review-form]");
  if (!form) {
    return;
  }

  const submit = form.querySelector("[data-review-submit]");
  const ratingInputs = Array.from(form.querySelectorAll('input[type="radio"][name$="rating"]'));

  if (!submit || ratingInputs.length === 0) {
    return;
  }

  const syncState = () => {
    submit.disabled = !ratingInputs.some((input) => input.checked);
  };

  ratingInputs.forEach((input) => {
    input.addEventListener("change", syncState);
  });

  syncState();
}

function setupSuggestionForm() {
  const form = document.querySelector("[data-suggestion-form]");
  if (!form) {
    return;
  }

  const title = form.querySelector("#suggestion-title");
  const author = form.querySelector("#suggestion-author");
  const year = form.querySelector("#suggestion-year");
  const submit = form.querySelector("[data-suggestion-submit]");

  if (!title || !author || !year || !submit) {
    return;
  }

  const syncState = () => {
    submit.disabled = !(title.value.trim() && author.value.trim() && year.value.trim());
  };

  [title, author, year].forEach((input) => {
    input.addEventListener("input", syncState);
    input.addEventListener("change", syncState);
  });

  form.addEventListener("submit", (event) => {
    if (submit.disabled) {
      event.preventDefault();
    }
  });

  syncState();
}

function setupCatalog() {
  const shell = document.querySelector("[data-catalog-shell]");
  if (!shell) {
    return;
  }

  const form = shell.querySelector("[data-catalog-form]");
  const searchInput = shell.querySelector("[data-catalog-search]");
  const sortInput = shell.querySelector("[data-catalog-sort-value]");
  const sortLabel = shell.querySelector("[data-catalog-sort-label]");
  const sortTrigger = shell.querySelector("[data-catalog-sort-trigger]");
  const modal = shell.querySelector("[data-catalog-sort-modal]");
  const closeTargets = shell.querySelectorAll("[data-catalog-sort-close]");
  const sortOptions = shell.querySelectorAll("[data-catalog-sort-option]");

  let modalWasOpen = false;

  const syncSortHighlight = () => {
    const currentSort = sortInput ? sortInput.value || "newest" : "newest";
    if (sortLabel) {
      sortLabel.textContent = catalogSortLabel(currentSort);
    }
    sortOptions.forEach((option) => {
      option.classList.toggle("catalog-sort-option--selected", option.dataset.catalogSortOption === currentSort);
    });
  };

  const openModal = () => {
    if (!modal) return;
    modal.hidden = false;
    modalWasOpen = true;
    syncSortHighlight();
    const selected = modal.querySelector(".catalog-sort-option--selected") || modal.querySelector("[data-catalog-sort-option]");
    if (selected instanceof HTMLElement) {
      selected.focus();
    }
  };

  const closeModal = () => {
    if (!modal) return;
    modal.hidden = true;
    modalWasOpen = false;
  };

  if (sortTrigger) {
    sortTrigger.addEventListener("click", () => {
      if (modal && !modal.hidden) {
        closeModal();
      } else {
        openModal();
      }
    });
  }

  closeTargets.forEach((target) => {
    target.addEventListener("click", closeModal);
  });

  sortOptions.forEach((option) => {
    option.addEventListener("click", async () => {
      if (!sortInput || !form) return;
      sortInput.value = option.dataset.catalogSortOption || "newest";
      syncSortHighlight();
      closeModal();
      await loadCatalog(buildCatalogUrl(form, 0), { historyMode: "push" });
    });
  });

  if (form) {
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      closeModal();
      await loadCatalog(buildCatalogUrl(form, 0), { historyMode: "push" });
    });
  }

  if (searchInput && form) {
    let searchTimer = null;

    const runSearch = () => {
      loadCatalog(buildCatalogUrl(form, 0), { historyMode: "replace" });
    };

    searchInput.addEventListener("input", () => {
      if (searchTimer) {
        window.clearTimeout(searchTimer);
      }
      searchTimer = window.setTimeout(runSearch, 120);
    });

    searchInput.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
      }
    });
  }

  const pagination = shell.querySelector("[data-catalog-pagination]");
  if (pagination) {
    pagination.addEventListener("click", async (event) => {
      const target = event.target;
      const link = target instanceof Element ? target.closest("a[href]") : null;
      if (!link) {
        return;
      }
      if (link.getAttribute("href")?.startsWith("javascript:")) {
        return;
      }
      event.preventDefault();
      await loadCatalog(link.href);
    });
  }

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && modal && !modal.hidden) {
      closeModal();
    }
  });

  window.addEventListener("popstate", () => {
    loadCatalog(window.location.pathname + window.location.search, { historyMode: "none" });
  });

  if (sortInput) {
    syncSortHighlight();
  }

  if (searchInput) {
    searchInput.autocomplete = "off";
  }

  if (modal && modalWasOpen) {
    openModal();
  }
}

document.addEventListener("DOMContentLoaded", () => {
  document.body.classList.add("auth-checking");
  void restoreRememberedSession().finally(() => {
    document.body.classList.remove("auth-checking");
    document.body.classList.add("auth-ready");
  });
  updateProfileRings();
  setupFlashToasts();
  setupProfileSettings();
  setupProfileBooksTabs();
  setupReviewForm();
  setupSuggestionForm();
  setupCatalog();
  document.querySelectorAll("[data-google-auth]").forEach((button) => {
    button.addEventListener("click", loginWithGoogle);
  });
  document.querySelectorAll("[data-logout]").forEach((button) => {
    button.addEventListener("click", logoutFirebase);
  });
});

window.loginWithGoogle = loginWithGoogle;
window.logoutFirebase = logoutFirebase;
