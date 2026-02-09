export interface PortfolioItem {
  id: number | null;
  title: string;
  description: string | null;
  imageUrl: string | null;
  thumbnailUrl: string | null;
  optimizedUrl: string | null;
  category: string | null;
  sortOrder: number;
}
