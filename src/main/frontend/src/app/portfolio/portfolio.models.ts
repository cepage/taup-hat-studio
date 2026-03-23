export interface PortfolioItem {
  id: number | null;
  title: string;
  description: string | null;
  imageUrl: string | null;
  thumbnailUrl: string | null;
  optimizedUrl: string | null;
  category: string | null;
  sortOrder: number;
  setId: number | null;
  setSortOrder: number;
}

export interface PortfolioSet {
  id: number | null;
  title: string;
  description: string | null;
  iconImageUrl: string | null;
  iconThumbnailUrl: string | null;
  iconOptimizedUrl: string | null;
  sortOrder: number;
  items?: PortfolioItem[];
}
