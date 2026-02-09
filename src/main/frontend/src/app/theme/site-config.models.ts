export interface SiteConfig {
  id: number | null;
  siteName: string;
  primaryColor: string;
  secondaryColor: string;
  accentColor: string;
  headingFont: string;
  bodyFont: string;
  heroImageUrl: string | null;
  aboutText: string | null;
  bigcartelUrl: string | null;
  socialLinks: string | null;
}

export interface SocialLink {
  platform: string;
  url: string;
}

/** Curated list of Google Fonts suitable for headings and body text. */
export const HEADING_FONTS = [
  'Playfair Display',
  'Merriweather',
  'Lora',
  'Libre Baskerville',
  'Cormorant Garamond',
  'Crimson Text',
  'EB Garamond',
  'Bitter',
  'Josefin Sans',
  'Montserrat',
  'Raleway',
  'Poppins',
  'Oswald',
  'Abril Fatface',
  'Bebas Neue',
];

export const BODY_FONTS = [
  'Open Sans',
  'Roboto',
  'Lato',
  'Source Sans 3',
  'Noto Sans',
  'Inter',
  'Work Sans',
  'Nunito',
  'PT Sans',
  'Rubik',
  'Karla',
  'Libre Franklin',
  'Cabin',
  'Fira Sans',
  'IBM Plex Sans',
];

export const SOCIAL_PLATFORMS = [
  'Instagram',
  'Twitter',
  'Bluesky',
  'Tumblr',
  'DeviantArt',
  'ArtStation',
  'Threads',
  'YouTube',
  'TikTok',
  'Facebook',
  'Mastodon',
  'Ko-fi',
  'Patreon',
];
