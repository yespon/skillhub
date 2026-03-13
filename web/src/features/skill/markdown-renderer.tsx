import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import { cn } from '@/shared/lib/utils'

interface MarkdownRendererProps {
  content: string
  className?: string
}

function stripFrontmatter(content: string) {
  return content.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n?/, '')
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  const markdown = stripFrontmatter(content).trim()

  return (
    <div
      className={cn(
        'max-w-none text-sm leading-7 text-foreground',
        '[&_a]:text-primary [&_a]:underline [&_a]:underline-offset-4 hover:[&_a]:text-primary/80',
        '[&_blockquote]:border-l-4 [&_blockquote]:border-border [&_blockquote]:pl-4 [&_blockquote]:italic [&_blockquote]:text-muted-foreground',
        '[&_code]:rounded-md [&_code]:bg-muted/70 [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:font-mono [&_code]:text-[0.9em]',
        '[&_h1]:mt-0 [&_h1]:mb-4 [&_h1]:text-3xl [&_h1]:font-bold [&_h1]:font-heading [&_h1]:leading-tight',
        '[&_h2]:mt-10 [&_h2]:mb-4 [&_h2]:border-b [&_h2]:border-border/60 [&_h2]:pb-2 [&_h2]:text-2xl [&_h2]:font-semibold [&_h2]:font-heading',
        '[&_h3]:mt-8 [&_h3]:mb-3 [&_h3]:text-xl [&_h3]:font-semibold [&_h3]:font-heading',
        '[&_h4]:mt-6 [&_h4]:mb-2 [&_h4]:text-lg [&_h4]:font-semibold [&_h4]:font-heading',
        '[&_hr]:my-8 [&_hr]:border-border/60',
        '[&_img]:rounded-xl [&_img]:border [&_img]:border-border/60',
        '[&_li]:my-1.5',
        '[&_ol]:my-4 [&_ol]:list-decimal [&_ol]:pl-6',
        '[&_p]:my-4',
        '[&_pre]:my-5 [&_pre]:overflow-x-auto [&_pre]:rounded-xl [&_pre]:border [&_pre]:border-border/60 [&_pre]:bg-slate-950 [&_pre]:p-4 [&_pre]:text-sm [&_pre]:text-slate-100',
        '[&_pre_code]:bg-transparent [&_pre_code]:p-0 [&_pre_code]:text-inherit',
        '[&_table]:my-6 [&_table]:w-full [&_table]:border-collapse [&_table]:overflow-hidden',
        '[&_tbody_tr]:border-t [&_tbody_tr]:border-border/60',
        '[&_td]:border [&_td]:border-border/60 [&_td]:px-3 [&_td]:py-2 [&_td]:align-top',
        '[&_th]:border [&_th]:border-border/60 [&_th]:bg-muted/50 [&_th]:px-3 [&_th]:py-2 [&_th]:text-left [&_th]:font-semibold',
        '[&_ul]:my-4 [&_ul]:list-disc [&_ul]:pl-6',
        className,
      )}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeSanitize, rehypeHighlight]}
      >
        {markdown}
      </ReactMarkdown>
    </div>
  )
}
