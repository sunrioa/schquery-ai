const STORAGE_KEY_THEME = 'theme'
const STORAGE_KEY_DARKMODE = 'darkMode'

export const getTheme = () => {
  const storedTheme = localStorage.getItem(STORAGE_KEY_THEME)
  if (storedTheme === 'dark' || storedTheme === 'light') return storedTheme

  const legacyDarkMode = localStorage.getItem(STORAGE_KEY_DARKMODE)
  if (legacyDarkMode === 'true') return 'dark'
  if (legacyDarkMode === 'false') return 'light'

  const attr = document.documentElement.getAttribute('data-theme')
  if (attr === 'dark') return 'dark'
  return 'light'
}

export const applyTheme = (theme) => {
  const html = document.documentElement
  const isDark = theme === 'dark'

  if (isDark) {
    html.setAttribute('data-theme', 'dark')
    html.classList.add('dark')
  } else {
    html.removeAttribute('data-theme')
    html.classList.remove('dark')
  }

  localStorage.setItem(STORAGE_KEY_THEME, isDark ? 'dark' : 'light')
  localStorage.setItem(STORAGE_KEY_DARKMODE, String(isDark))

  window.dispatchEvent(new CustomEvent('theme-change', { detail: { isDark } }))
}

export const initTheme = () => {
  applyTheme(getTheme())
}

export const toggleTheme = () => {
  applyTheme(getTheme() === 'dark' ? 'light' : 'dark')
}

export const isDarkTheme = () => getTheme() === 'dark'

