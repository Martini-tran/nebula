ALTER TABLE `blog_post`
  ADD COLUMN `post_type` varchar(20) NOT NULL DEFAULT 'article' COMMENT '内容类型：article(文章)/essay(随笔)' AFTER `author_id`;

CREATE INDEX `idx_blog_post_type_status_published_at`
  ON `blog_post` (`post_type`, `status`, `visibility`, `published_at`);
