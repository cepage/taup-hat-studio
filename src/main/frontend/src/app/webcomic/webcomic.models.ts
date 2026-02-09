export interface WebcomicSeries {
  id: number | null;
  title: string;
  slug: string;
  description: string;
  coverImageUrl: string | null;
  sortOrder: number;
  active: boolean;
  issues?: WebcomicIssue[];
}

export interface WebcomicIssue {
  id: number | null;
  seriesId: number;
  issueNumber: number;
  title: string;
  coverImageUrl: string | null;
  publishDate: string | null;
  published: boolean;
  pages?: WebcomicPage[];
}

export interface WebcomicPage {
  id: number | null;
  issueId: number;
  pageNumber: number;
  imageUrl: string;
  thumbnailUrl: string | null;
  optimizedUrl: string | null;
}
