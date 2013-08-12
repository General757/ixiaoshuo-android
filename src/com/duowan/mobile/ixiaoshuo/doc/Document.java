package com.duowan.mobile.ixiaoshuo.doc;

import android.graphics.Rect;
import android.util.Log;
import com.duowan.mobile.ixiaoshuo.pojo.Book;
import com.duowan.mobile.ixiaoshuo.pojo.Chapter;
import com.duowan.mobile.ixiaoshuo.ui.RenderPaint;
import com.duowan.mobile.ixiaoshuo.utils.CharsetUtil;
import com.duowan.mobile.ixiaoshuo.utils.Encoding;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;

/**
 * @author neevek
 * This class implements the logics to read a plain text file by block,
 * and provides funcionalities to turn next & previous page.
 * this class will ensure that memory usage be always kept to a certain limit
 * (i.e. will not grow unlimitedly even if the file is large, even in hundred metabytes.)
 */
public abstract class Document {
	protected static final String TAG = "Document";
	private final static char NEW_LINE_CHAR = '\n';
	private final static String NEW_LINE_STR = "\n";

	protected RenderPaint mPaint;

	private int mMaxPageLineCount;
	private int mMaxCharCountPerPage;
	private int mMaxCharCountPerLine;
	private int mCurContentHeight;

	protected Book mBook;
	protected Encoding mEncoding;
	
	protected RandomAccessFile mRandBookFile;
	protected long mFileSize;				// in bytes
	
	protected byte[] mByteBuffer;
	protected StringBuilder mContentBuf = new StringBuilder();
	protected int mPageCharOffsetInBuffer;		// char offset in buffer (current char offset in mContentBuf)
	
	protected long mReadByteBeginOffset;		// current read buffer start byte position in the file
	protected long mReadByteEndOffset;			// current read buffer end byte position in the file
	protected long mPageBeginPosition;			// page byte begin position in file
	protected int mNewlineIndex;				// for storing index of newline when turning next or previous pages
	protected int mEndCharIndex;				// for storing end index of a certain paragraph when turning next or previous pages
	protected int mCharCountOfPage;				// for storing temporary values when getLine()
	
	// for storing byte count of each block we read when reading through the file
	// for prevent mContentBuf from unlimitedly growing, we need to throw away characters at the
	// head of mContentBuf along the way we read through the file, and mByteMetaList is for avoiding
	// cutting off half a GBK char or a UTF-8 char at the head of the buffer
	protected LinkedList<ByteMeta> mByteMetaList = new LinkedList<ByteMeta>();
	// for turning preivous page, for storing temporary offsets
	LinkedList<Integer> mCharOffsetList = new LinkedList<Integer>();
	// for fast turning previous page
	LinkedList<Integer> mCharOffsetCache = new LinkedList<Integer>();

	public Document(Book book, RenderPaint paint) {
		mByteBuffer = new byte[8192];
		mPaint = paint;
		resetTextHeight();
		mBook = book;
	}

	private void resetTextHeight() {
		float lineHeight = mPaint.getTextHeight() + mPaint.getLineSpacing();

		mMaxPageLineCount = (int)(mPaint.getRenderHeight() / lineHeight);
		if((mPaint.getRenderHeight() % lineHeight) >= mPaint.getTextHeight()) {
			++mMaxPageLineCount;
		}

		mMaxCharCountPerLine = mPaint.getRenderWidth() / mPaint.getTextWidth();

		Rect rect = new Rect();
		mPaint.getTextBounds("i", 0, 1, rect);
		mMaxCharCountPerPage = mPaint.getRenderWidth() / (rect.right - rect.left) * mMaxPageLineCount;
	}
	
	public final void onResetTextSize() {
		resetTextHeight();
	}
	
	protected final void scrollDownBuffer() {
		// throw away characters at the head of the buffer if some requrements are met  
		if(mByteMetaList.size() > 0 && mPageCharOffsetInBuffer >= mByteMetaList.peek().charCount) {
			ByteMeta meta = mByteMetaList.removeFirst();
			mContentBuf.delete(0, meta.charCount);
			
			// "meta.charCount" characters were thrown away, so we have to minus the "offset in buffer" by "meta.charCount"
			mPageCharOffsetInBuffer -= meta.charCount;
			// increase the start byte offset where we start reading the file
			mReadByteBeginOffset = meta.byteOffset + meta.byteCount;
		}
		
		try {
			// position the file pointer at the end position of the last/previous read
			mRandBookFile.seek(mReadByteEndOffset);
			int lenRead = mRandBookFile.read(mByteBuffer);
			if(lenRead > 0) {
				// skip last incomplete bytes if there are some of them
				lenRead -= CharsetUtil.getByteCountOfLastIncompleteChar(mByteBuffer, lenRead, mEncoding);
				
				// append the text to the end of the buffer
				String content = new String(mByteBuffer, 0, lenRead, mEncoding.getName());
				mContentBuf.append(content);
				
				// store the meta data of the current read
				mByteMetaList.add(new ByteMeta(mReadByteEndOffset, lenRead, content.length()));
				// grow the end byte offset
				mReadByteEndOffset += lenRead;
			}
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	protected final void scrollUpBuffer() {
		try {
			// we read backwards at most "mByteBuffer.length" bytes each time
			long positionToSeek = mReadByteBeginOffset - mByteBuffer.length;
			int bytesToRead = mByteBuffer.length;
			if(positionToSeek < 0) {	// if we reach the beginning of the file
				positionToSeek = 0;
				bytesToRead = (int) mReadByteBeginOffset;
			}
			mRandBookFile.seek(positionToSeek);
			int lenRead = mRandBookFile.read(mByteBuffer, 0, bytesToRead);
			if(lenRead > 0) {
				int incompleteByteCount = 0;
				if(positionToSeek > 0) {	// if we are not at the beginning of the file
					incompleteByteCount = CharsetUtil.getByteCountOfFirstIncompleteChar(mByteBuffer, lenRead, mEncoding);
					if(incompleteByteCount > 0) {
						lenRead -= incompleteByteCount;
					}
				}
				String content = new String(mByteBuffer, incompleteByteCount, lenRead, mEncoding.getName());
				mContentBuf.insert(0, content);
				
				// since we are reading backwards(towards the beginning of the file), we need to decrease
				// the current "start byte offset" from where we start reading the file
				mReadByteBeginOffset -= lenRead;
				mPageCharOffsetInBuffer += content.length();
				// prepend the meta data to the beginning of the file
				mByteMetaList.addFirst(new ByteMeta(mReadByteBeginOffset, lenRead, content.length()));
			}
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	public int getCharCountOfCurPage () {
		return mCharCountOfPage;
	}
	
	public boolean turnNextPage () {
		if(mPageCharOffsetInBuffer + mCharCountOfPage < mContentBuf.length()) {
			mCharOffsetCache.add(mPageCharOffsetInBuffer);
			if(mCharOffsetCache.size() > 20)
				mCharOffsetCache.removeFirst();
			// for scrolldown(turning next page), we simply add up mCharOffsetOfPage to the current char offset 
			mPageCharOffsetInBuffer += mCharCountOfPage;
			return true;
		}
		return false;
	}
	
	public boolean turnPreviousPage () {
		if(mCharOffsetCache.size() > 0) {
			mPageCharOffsetInBuffer = mCharOffsetCache.removeLast();
			return true;
		}
		// check if we need to read some bytes and prepend them to the beginning of the buffer(if we are near the beginning of the buffer)
		if(mReadByteBeginOffset > 0 && mPageCharOffsetInBuffer - mMaxCharCountPerPage < 0) {
			scrollUpBuffer();
		}
		if(mPageCharOffsetInBuffer > 0) {
			mCharOffsetList.clear();
			// *previous* page should start from here *at most*(if the *previous* page contains mMaxCharCountPerPage characters, which is quite unusual)
			int beginCharOffset = mPageCharOffsetInBuffer - mMaxCharCountPerPage;
			if(beginCharOffset < 0) {	// if we reach the beginning of the file
				beginCharOffset = 0;
			} else {
				// try finding a NEWLINE(paragraph boundary)
				while (beginCharOffset > 0) {
					if(mContentBuf.lastIndexOf(NEW_LINE_STR, beginCharOffset) != -1)
						break;
					--beginCharOffset;
				}
			}
			// end(exclusive) of the *previous* page is the start(inclusive) of the current page
			int endCharOffset = mPageCharOffsetInBuffer;
			// if last character of the previous page is a newline, we MUST skip it, beacause this newline
			// is for separating the last paragraph of the previous page and the first paragraph of the 
			// current page(if it does exist), we don't need it because we are going to segment the text
			// by paragraphs of the previous page(only the previous page, not the current page)
			if(mContentBuf.charAt(endCharOffset - 1) == NEW_LINE_CHAR) {
				--endCharOffset;	// ignore last newline of the previous page
			}
			// from the end to the beginning, we search for the first newline(of course, the actual *first* newline might have 
			// already been skipped, then we are searching for the second one if you are confusing^_^)
			int lineCharOffset = beginCharOffset;
			int newlineOffset = mContentBuf.lastIndexOf(NEW_LINE_STR, endCharOffset - 1);

			// found a newline in previous page
			if (newlineOffset != -1) {
				lineCharOffset = newlineOffset + 1;
				
				int lineCount = 0;
				while (endCharOffset > beginCharOffset) {
					if (lineCharOffset == endCharOffset) {
						newlineOffset = mContentBuf.lastIndexOf(NEW_LINE_STR, endCharOffset - 1);
						if(newlineOffset != -1) {
							lineCharOffset = newlineOffset + 1;
						} else { 
							lineCharOffset = beginCharOffset;
						}

						lineCount = 0;
					}
					int charCount = mPaint.breakText(mContentBuf, lineCharOffset, endCharOffset, lineCount == 0);
					
					// note: addFirst
					mCharOffsetList.addFirst(lineCharOffset);
					
					lineCharOffset += charCount;
					++lineCount;
					if (lineCharOffset == endCharOffset) {
						while (--lineCount >= 0) {
							// note: removeFirst and then add to the last
							mCharOffsetList.add(mCharOffsetList.removeFirst());
						}
						if (mCharOffsetList.size() >= mMaxPageLineCount) break;
						
						lineCharOffset = endCharOffset = newlineOffset;
					}
				}
			} else {
				while (lineCharOffset < endCharOffset) {
					int charCount = mPaint.breakText(mContentBuf, lineCharOffset, endCharOffset, false);
					mCharOffsetList.addFirst(lineCharOffset);
					lineCharOffset += charCount;
				}
			}
			
			int contentHeight = 0;
			for (int i = 0; i < mCharOffsetList.size(); ++i) {
				if (contentHeight > 0) {
					boolean isNewline = mContentBuf.charAt(mCharOffsetList.get(i - 1) - 1) == NEW_LINE_CHAR;
					if(isNewline) contentHeight += mPaint.getParagraphSpacing();
					else contentHeight += mPaint.getLineSpacing();
				}
				contentHeight += mPaint.getTextHeight();
				if (contentHeight > mPaint.getRenderHeight()) {
					mPageCharOffsetInBuffer = mCharOffsetList.get(i - 1);
					break;
				} else if (i == 0) {
					mPageCharOffsetInBuffer = mCharOffsetList.get(mCharOffsetList.size() - 1);
				}
			}
			
			return true;
		}
		return false;
	}
	
	public void prepareGetLines () {
		mCurContentHeight = 0;
		mCharCountOfPage = 0;
		
		// only do this check once per page
		if(mReadByteEndOffset < mFileSize && mPageCharOffsetInBuffer + mMaxCharCountPerPage > mContentBuf.length())
			scrollDownBuffer();
		
		// reset newline to the current offset
		mNewlineIndex = mPageCharOffsetInBuffer;
	}
	
	public final static byte GET_NEXT_LINE_FLAG_HAS_NEXT_LINE = 1;
	public final static byte GET_NEXT_LINE_FLAG_SHOULD_JUSTIFY = 1 << 1;
	public final static byte GET_NEXT_LINE_FLAG_PARAGRAPH_ENDS = 1 << 2;
	
	public final byte getNextLine (StringBuilder sb) {
		// reach the end of the file
		if (mPageCharOffsetInBuffer + mCharCountOfPage >= mContentBuf.length()) return 0;

		mCurContentHeight += mPaint.getTextHeight();
		if (mCurContentHeight > mPaint.getRenderHeight()) return 0;
		byte flags = GET_NEXT_LINE_FLAG_HAS_NEXT_LINE;

		int index = mPageCharOffsetInBuffer + mCharCountOfPage;
		if (index == mNewlineIndex) {
			mNewlineIndex = mContentBuf.indexOf(NEW_LINE_STR, mNewlineIndex);
			mEndCharIndex = (mNewlineIndex != -1) ? mNewlineIndex : mContentBuf.length();
		}

		boolean needIndent = false;
		if (index > 0 && mPaint.getFirstLineIndent().length() > 0) {
			needIndent = mContentBuf.charAt(index - 1) == NEW_LINE_CHAR;
		}

		int charCount = mPaint.breakText(mContentBuf, index, mEndCharIndex, needIndent);
		if (charCount > 0) {
			sb.append(mContentBuf, index, index + charCount);
			LayoutUtil.trimWhiteSpaces(sb);
			mCharCountOfPage += charCount;

			// append indent if carriage return character before the line
			if (needIndent) sb.insert(0, mPaint.getFirstLineIndent());
		}

		int endIndex = mPageCharOffsetInBuffer + mCharCountOfPage;
		if (endIndex == mNewlineIndex) {
			++mCharCountOfPage;
			++mNewlineIndex;
			mCurContentHeight += mPaint.getParagraphSpacing();
			flags |= GET_NEXT_LINE_FLAG_PARAGRAPH_ENDS;
		} else {
			// justify when text fill whole line
			if (endIndex < mContentBuf.length()) {
				flags |= GET_NEXT_LINE_FLAG_SHOULD_JUSTIFY;
			}
			mCurContentHeight += mPaint.getLineSpacing();
		}

		return flags;
	}

	protected void invalidatePrevPagesCache () {
		mCharOffsetCache.clear();
	}

	public void calculatePagePosition() {
		try {
			mPageBeginPosition = mReadByteBeginOffset + mContentBuf.substring(0, mPageCharOffsetInBuffer).getBytes(mEncoding.getName()).length;
			mBook.getReadingChapter().setBeginPosition((int) mPageBeginPosition);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}
	
	public final String getCurrentPageFrontText(int length) {
		if(mPageCharOffsetInBuffer + length < mContentBuf.length())
			return mContentBuf.substring(mPageCharOffsetInBuffer, mPageCharOffsetInBuffer + length);
		return mContentBuf.substring(mPageCharOffsetInBuffer);
	}
	
//	protected long calculatePosition(float percentage) {
//		return percentage >= 1f ? mFileSize - 200 : (long) (mFileSize * percentage);
//	}

	protected final long getSafetyPosition(long fileBeginPosition) {
		if(fileBeginPosition == 0) return 0;
		try {
			byte[] tempContentBuf = new byte[1024 * 4];
			mRandBookFile.seek(fileBeginPosition);
			int lenRead = mRandBookFile.read(tempContentBuf);

			int skippedBytes = CharsetUtil.getByteCountOfFirstIncompleteChar(tempContentBuf, lenRead, mEncoding);
			if (tempContentBuf[skippedBytes] == NEW_LINE_CHAR) {
				++skippedBytes;
			} else if (tempContentBuf[skippedBytes] == '\r') {
				++skippedBytes;
				if (tempContentBuf[skippedBytes] == NEW_LINE_CHAR)
					++skippedBytes;
			}
			return fileBeginPosition + skippedBytes;
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
		return fileBeginPosition;
	}
	
	protected final long getBackmostPosition() {
		long beginPosition = mFileSize - Double.valueOf(mMaxCharCountPerLine * (mMaxPageLineCount / 3) * mEncoding.getMaxCharLength()).intValue();
		return beginPosition < 1 ? 0 : getSafetyPosition(beginPosition);
	}

	public boolean hasNextChapter() { return mBook.getNextChapter() != null; }
	public boolean hasPreviousChapter() { return mBook.getPreviousChapter() != null; }
	
//	public abstract boolean adjustReadingProgress(float percentage);
	public abstract boolean adjustReadingProgress(Chapter chapter);
//	public abstract float calculateReadingProgress();
	
	class ByteMeta {
		long byteOffset;
		int byteCount;
		int charCount;
		public ByteMeta(long byteOffset, int byteCount, int charCount) {
			this.byteOffset = byteOffset;
			this.byteCount = byteCount;
			this.charCount = charCount;
		}
	}

	public Book getBook() {
		return mBook;
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			if(mRandBookFile != null) mRandBookFile.close();
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
		super.finalize();
		System.gc();
	}
	
}